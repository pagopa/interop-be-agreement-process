package it.pagopa.pdnd.interop.uservice.agreementprocess.error

final case class AgreementNotFound(agreementId: String) extends Throwable(s"Agreement $agreementId not found")
