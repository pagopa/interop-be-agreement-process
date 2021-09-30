package it.pagopa.pdnd.interop.uservice.agreementprocess.error

final case class AgreementAttributeNotFound(attributeId: String)
    extends Throwable(s"EService attribute $attributeId not found in agreement")
