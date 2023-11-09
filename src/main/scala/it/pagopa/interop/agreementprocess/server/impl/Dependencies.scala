package it.pagopa.interop.agreementprocess.server.impl

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import cats.syntax.all._
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.api.impl.ResponseHandlers.serviceCode
import it.pagopa.interop.agreementprocess.api.impl.{HealthApiMarshallerImpl, HealthServiceApiImpl, _}
import it.pagopa.interop.agreementprocess.api.{AgreementApi, HealthApi}
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.agreementprocess.service.impl._
import it.pagopa.interop.authorizationmanagement.client.api.PurposeApi
import it.pagopa.interop.selfcare.v2.client.api.{InstitutionsApi, UsersApi}
import it.pagopa.interop.commons.cqrs.service.{MongoDbReadModelService, ReadModelService}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.queue.config.SQSHandlerConfig
import it.pagopa.interop.commons.queue.impl.SQSHandler
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.{Problem => CommonProblem}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val loggerTI: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog]("OAuth2JWTValidatorAsContexts")

  implicit val selfcareV2ClientApiKeyValue: SelfcareV2ClientApiKeyValue = SelfcareV2ClientApiKeyValue()

  implicit val readModelService: ReadModelService = new MongoDbReadModelService(
    ApplicationConfiguration.readModelConfig
  )

  def agreementManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AgreementManagementService =
    new AgreementManagementServiceImpl(
      AgreementManagementInvoker(blockingEc)(actorSystem.classicSystem),
      AgreementManagementApi(ApplicationConfiguration.agreementManagementURL)
    )

  def authorizationManagement(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AuthorizationManagementService =
    new AuthorizationManagementServiceImpl(
      AuthorizationManagementInvoker(blockingEc)(actorSystem.classicSystem),
      new PurposeApi(ApplicationConfiguration.authorizationManagementURL)
    )

  def selfcareV2Client(implicit actorSystem: ActorSystem[_]): SelfcareV2ClientService =
    new SelfcareV2ClientServiceImpl(
      SelfcareV2ClientInvoker()(actorSystem.classicSystem),
      new InstitutionsApi(ApplicationConfiguration.selfcareV2ClientURL),
      new UsersApi(ApplicationConfiguration.selfcareV2ClientURL)
    )

  def getJwtValidator(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey] = keyset

        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  def fileManager(blockingEc: ExecutionContextExecutor): FileManager =
    FileManager.get(ApplicationConfiguration.storageKind match {
      case "S3"   => FileManager.S3
      case "file" => FileManager.File
      case _      => throw new Exception("Incorrect File Manager")
    })(blockingEc)

  def certifiedMailQueueName(blockingEc: ExecutionContextExecutor): QueueService = {
    val config: SQSHandlerConfig =
      SQSHandlerConfig(
        queueUrl = ApplicationConfiguration.certifiedMailQueueName,
        messageGroupId = ApplicationConfiguration.certifiedMailMessageGroupId.some
      )
    val sqsHandler: SQSHandler   = SQSHandler(config)(blockingEc)
    new QueueServiceImpl(sqsHandler)
  }

  def archivingPurposesQueueName(blockingEc: ExecutionContextExecutor): QueueService = {
    val config: SQSHandlerConfig = SQSHandlerConfig(queueUrl = ApplicationConfiguration.archivingPurposesQueueName)
    val sqsHandler: SQSHandler   = SQSHandler(config)(blockingEc)
    new QueueServiceImpl(sqsHandler)
  }

  def archivingEservicesQueueName(blockingEc: ExecutionContextExecutor): QueueService = {
    val config: SQSHandlerConfig = SQSHandlerConfig(queueUrl = ApplicationConfiguration.archivingEservicesQueueName)
    val sqsHandler: SQSHandler   = SQSHandler(config)(blockingEc)
    new QueueServiceImpl(sqsHandler)
  }

  def agreementApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AgreementApi =
    new AgreementApi(
      AgreementApiServiceImpl(
        agreementManagement(blockingEc),
        CatalogManagementServiceImpl,
        TenantManagementServiceImpl,
        AttributeManagementServiceImpl,
        authorizationManagement(blockingEc),
        selfcareV2Client,
        PDFCreator,
        fileManager(blockingEc),
        OffsetDateTimeSupplier,
        UUIDSupplier,
        certifiedMailQueueName(blockingEc),
        archivingPurposesQueueName(blockingEc),
        archivingEservicesQueueName(blockingEc)
      ),
      AgreementApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator),
    loggingEnabled = false
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      CommonProblem(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report), serviceCode, None)
    complete(error.status, error)
  }
}
