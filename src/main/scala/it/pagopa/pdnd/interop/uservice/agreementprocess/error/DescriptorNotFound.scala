package it.pagopa.pdnd.interop.uservice.agreementprocess.error

final case class DescriptorNotFound(eserviceId: String, descriptorId: String)
    extends Throwable(s"Descriptor $descriptorId not found in eservice $eserviceId")
