package it.pagopa.interop.agreementprocess.service

import it.pagopa.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.interop.agreementprocess.model.{Attribute, Attributes}

import scala.concurrent.{ExecutionContext, Future}

trait AttributeManagementService {
  def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute]
  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[ClientAttribute]
}

object AttributeManagementService {
  def getAttributes(certified: Seq[ClientAttribute], declared: Seq[ClientAttribute], verified: Seq[ClientAttribute])(
    implicit ec: ExecutionContext
  ): Future[Attributes] = for {
    certified <- Future.traverse(certified)(toApi)
    declared  <- Future.traverse(declared)(toApi)
    verified  <- Future.traverse(verified)(toApi)
  } yield Attributes(certified = certified, declared = declared, verified = verified)

  def toApi(attr: ClientAttribute): Future[Attribute] = Future.fromTry {
    for {
      id <- attr.id.toUUID
    } yield Attribute(
      id = id,
      code = attr.code,
      description = attr.description,
      origin = attr.origin,
      name = attr.name,
      explicitAttributeVerification = None,
      verified = None,
      verificationDate = None,
      validityTimespan = None
    )
  }

}
