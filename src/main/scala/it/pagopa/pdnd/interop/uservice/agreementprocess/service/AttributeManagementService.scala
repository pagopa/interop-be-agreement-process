package it.pagopa.pdnd.interop.uservice.agreementprocess.service

import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Attribute, Attributes}

import scala.concurrent.Future

trait AttributeManagementService {
  def getAttribute(attributeId: String): Future[ClientAttribute]
}

object AttributeManagementService {
  def getAttributes(
    certified: Seq[ClientAttribute],
    declared: Seq[ClientAttribute],
    verified: Seq[ClientAttribute]
  ): Future[Attributes] = Future.successful {
    Attributes(
      certified = certified.flatMap(toApi),
      declared = declared.flatMap(toApi),
      verified = verified.flatMap(toApi)
    )
  }

  def toApi(attr: ClientAttribute): Option[Attribute] = for {
    code   <- attr.code
    origin <- attr.origin
  } yield Attribute(code = code, description = attr.description, origin = origin, name = attr.name)
}
