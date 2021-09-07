package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Attribute, Attributes}
import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{
  AttributeManagementService,
  AttributeRegistryManagementInvoker
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.{Attribute => ClientAttribute}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@SuppressWarnings(
  Array(
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.ToString"
  )
)
final case class AttributeManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeManagementService {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttributes(certified: Seq[String], declared: Seq[String], verified: Seq[String]): Future[Attributes] =
    for {
      c <- Future.traverse(certified)(getAttribute)
      d <- Future.traverse(declared)(getAttribute)
      v <- Future.traverse(verified)(getAttribute)
    } yield Attributes(certified = c.flatMap(toApi), declared = d.flatMap(toApi), verified = v.flatMap(toApi))

  private def getAttribute(attributeId: String): Future[ClientAttribute] = for {
    uuid      <- Future.fromTry(Try(UUID.fromString(attributeId)))
    attribute <- attributeByUUID(uuid)
  } yield attribute

  private def attributeByUUID(attributeId: UUID): Future[ClientAttribute] = {
    val request = api.getAttributeById(attributeId) // TODO maybe a batch request is better
    invoker
      .execute(request)
      .map { x =>
        logger.info(s"Retrieving attribute ${x.code} > ${x.content}")
        x.content
      }
      .recoverWith { case ex =>
        logger.error(s"Retrieving attribute by UUID FAILED: ${ex.getMessage}")
        Future.failed[ClientAttribute](ex)
      }
  }

  private def toApi(attr: ClientAttribute): Option[Attribute] = for {
    code   <- attr.code
    origin <- attr.origin
  } yield Attribute(code = code, description = attr.description, origin = origin, name = attr.name)

}
