package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.ApiRequest
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementEnums, VerifiedAttribute}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.BearerToken
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{Attribute, Attributes}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.ToString"
  )
)
final case class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def activateById(bearerToken: String, agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.activateAgreement(agreementId)
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Activating agreement ${x.code}")
        logger.info(s"Activating agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Activating agreement FAILED: ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def getAgreementById(bearerToken: String, agreementId: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId)(BearerToken(bearerToken))
    invoker
      .execute[Agreement](request)
      .map { x =>
        logger.info(s"Retrieving agreement ${x.code}")
        logger.info(s"Retrieving agreement ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving agreement ${ex.getMessage}")
        Future.failed[Agreement](ex)
      }
  }

  override def checkAgreementActivation(agreement: Agreement): Future[Agreement] = {
    Future.fromTry(
      Either
        .cond(
          agreement.status == AgreementEnums.Status.Active,
          agreement,
          new RuntimeException(s"Agreement ${agreement.id} is not active")
        )
        .toTry
    )
  }

  override def isPending(agreement: Agreement): Future[Agreement] = {
    Future.fromTry(
      Either
        .cond(
          agreement.status == AgreementEnums.Status.Pending,
          agreement,
          new RuntimeException(s"Agreement ${agreement.id} status is ${agreement.status}")
        )
        .toTry
    )
  }

  override def verifyAttributes(
    consumerAttributesIds: Seq[String],
    eserviceAttributes: Attributes,
    agreementVerifiedAttributes: Seq[VerifiedAttribute]
  ): Future[Boolean] = {

    def checkAttributesFn(attributes: Seq[Attribute]) = checkAttributes(consumerAttributesIds)(attributes)
    def hasCertified()                                = checkAttributesFn(eserviceAttributes.certified)
    def hasVerified()                                 = agreementVerifiedAttributes.foldLeft(true)((acc, attr) => attr.verified && acc)

    if (hasCertified() && hasVerified())
      Future.successful(true)
    else
      Future.failed[Boolean](new RuntimeException("This agreement does not have all the expected attributes"))
  }

  private def checkAttributes(consumerAttributes: Seq[String])(attributes: Seq[Attribute]): Boolean = {
    attributes.map(hasAttribute(consumerAttributes)).fold(true)(_ && _)
  }

  private def hasAttribute(consumerAttributes: Seq[String])(attribute: Attribute): Boolean = {
    val hasSimpleAttribute = () =>
      attribute.simple
        .fold(true)(simple => consumerAttributes.contains(simple))

    val hasGroupAttributes = () =>
      attribute.group.fold(true)(orAttributes => consumerAttributes.intersect(orAttributes).nonEmpty)

    hasSimpleAttribute() && hasGroupAttributes()
  }
}
