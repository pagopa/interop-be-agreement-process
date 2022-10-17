package it.pagopa.interop.agreementprocess.service

import akka.http.scaladsl.model.MediaTypes
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, DocumentSeed, UpdateAgreementSeed}
import it.pagopa.interop.agreementprocess.common.system.ApplicationConfiguration
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{MissingUserInfo, StampNotFound}
import it.pagopa.interop.agreementprocess.service.util.PDFPayload
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource
import it.pagopa.interop.tenantmanagement.client.model.{
  CertifiedTenantAttribute,
  DeclaredTenantAttribute,
  Tenant,
  VerifiedTenantAttribute
}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

final class AgreementContractCreator(
  pdfCreator: PDFCreator,
  fileManager: FileManager,
  uuidSupplier: UUIDSupplier,
  agreementManagementService: AgreementManagementService,
  attributeManagementService: AttributeManagementService,
  partyManagementService: PartyManagementService,
  userRegistry: UserRegistryService,
  offsetDateTimeSupplier: OffsetDateTimeSupplier
) {

  private[this] val agreementTemplate = Source
    .fromResource("agreementTemplate/index.html")
    .getLines()
    .mkString(System.lineSeparator())

  private val agreementDocumentSuffix: String = "agreement_contract.pdf"
  private val contractPrettyName: String      = "Richiesta di fruizione"

  def create(agreement: Agreement, eService: EService, consumer: Tenant, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Unit] = for {
    pdfPayload <- getPdfPayload(agreement, eService, consumer, seed)
    document   <- pdfCreator.create(agreementTemplate, pdfPayload)
    documentName = createAgreementDocumentName(agreement.consumerId, agreement.producerId)
    documentId   = uuidSupplier.get()
    path <- fileManager.storeBytes(
      ApplicationConfiguration.storageContainer,
      s"${ApplicationConfiguration.agreementContractPath}/${agreement.id.toString()}"
    )(documentId.toString(), documentName, document)
    _    <- agreementManagementService.addAgreementContract(
      agreement.id,
      DocumentSeed(documentId, documentName, contractPrettyName, MediaTypes.`application/pdf`.value, path)
    )
  } yield ()

  def getAttributeInvolved(consumer: Tenant, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[
    (
      Seq[(ClientAttribute, CertifiedTenantAttribute)],
      Seq[(ClientAttribute, DeclaredTenantAttribute)],
      Seq[(ClientAttribute, VerifiedTenantAttribute)]
    )
  ] = {
    def getCertified: Future[Seq[(ClientAttribute, CertifiedTenantAttribute)]] = {
      val attributes =
        consumer.attributes.flatMap(_.certified).filter(c => seed.certifiedAttributes.map(_.id).contains(c.id))
      Future.traverse(attributes)(attr =>
        attributeManagementService.getAttribute(attr.id.toString()).map(ca => ca -> attr)
      )
    }

    def getDeclared: Future[Seq[(ClientAttribute, DeclaredTenantAttribute)]] = {
      val attributes =
        consumer.attributes.flatMap(_.declared).filter(c => seed.declaredAttributes.map(_.id).contains(c.id))
      Future.traverse(attributes)(attr =>
        attributeManagementService.getAttribute(attr.id.toString()).map(ca => ca -> attr)
      )
    }

    def getVerified: Future[Seq[(ClientAttribute, VerifiedTenantAttribute)]] = {
      val attributes =
        consumer.attributes.flatMap(_.verified).filter(c => seed.verifiedAttributes.map(_.id).contains(c.id))
      Future.traverse(attributes)(attr =>
        attributeManagementService.getAttribute(attr.id.toString()).map(ca => ca -> attr)
      )
    }

    for {
      certified <- getCertified
      declared  <- getDeclared
      verified  <- getVerified
    } yield (certified, declared, verified)

  }

  def getSubmissionInfo(
    seed: UpdateAgreementSeed
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[(String, OffsetDateTime)] =
    for {
      submission <- seed.stamps.submission.toFuture(StampNotFound("submission"))
      response   <- userRegistry.getUserById(submission.who)
      submitter  <- getUserText(response).toFuture(MissingUserInfo(submission.who))
    } yield (submitter, submission.when)

  def getActivationInfo(
    seed: UpdateAgreementSeed
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[(String, OffsetDateTime)] =
    for {
      activation <- seed.stamps.activation.toFuture(StampNotFound("activation"))
      response   <- userRegistry.getUserById(activation.who)
      activator  <- getUserText(response).toFuture(MissingUserInfo(activation.who))
    } yield (activator, activation.when)

  def getUserText(user: UserResource): Option[String] = for {
    name       <- user.name
    familyName <- user.familyName
    fiscalCode <- user.fiscalCode
  } yield s"${name.value} ${familyName.value} ($fiscalCode)"

  def getPdfPayload(agreement: Agreement, eService: EService, consumer: Tenant, seed: UpdateAgreementSeed)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[PDFPayload] = {
    for {
      (certified, declared, verified)  <- getAttributeInvolved(consumer, seed)
      producerParty                    <- partyManagementService.getInstitution(agreement.producerId)
      consumerParty                    <- partyManagementService.getInstitution(agreement.consumerId)
      (submitter, submissionTimestamp) <- getSubmissionInfo(seed)
      (activator, activationTimestamp) <- getActivationInfo(seed)
    } yield PDFPayload(
      today = offsetDateTimeSupplier.get(),
      agreementId = agreement.id,
      eService = eService.name,
      producer = producerParty,
      consumer = consumerParty,
      certified = certified,
      declared = declared,
      verified = verified,
      submitter = submitter,
      submissionTimestamp = submissionTimestamp,
      activator = activator,
      activationTimestamp = activationTimestamp
    )
  }

  def createAgreementDocumentName(consumerId: UUID, producerId: UUID): String = {
    val timestamp: String = offsetDateTimeSupplier.get().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))

    s"${consumerId.toString()}_${producerId.toString()}_${timestamp}_$agreementDocumentSuffix"
  }

}
