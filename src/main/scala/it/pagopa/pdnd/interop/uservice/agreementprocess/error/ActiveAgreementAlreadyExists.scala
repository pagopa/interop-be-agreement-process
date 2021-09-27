package it.pagopa.pdnd.interop.uservice.agreementprocess.error

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.Agreement

final case class ActiveAgreementAlreadyExists(agreement: Agreement)
    extends Throwable(
      s"Active Agreement already exists for Producer = ${agreement.producerId.toString}, Consumer = ${agreement.consumerId.toString}, EService = ${agreement.eserviceId.toString}, Descriptor = ${agreement.descriptorId.toString}"
    )
