package it.pagopa.interop.agreementprocess.service.util

import it.pagopa.interop.agreementprocess.service.ClientAttribute
import it.pagopa.interop.selfcare.partymanagement.client.model.Institution
import it.pagopa.interop.tenantmanagement.client.model.{
  CertifiedTenantAttribute,
  DeclaredTenantAttribute,
  VerifiedTenantAttribute
}

import java.time.OffsetDateTime
import java.util.UUID

final case class PDFPayload(
  today: OffsetDateTime,
  agreementId: UUID,
  eService: String,
  producer: Institution,
  consumer: Institution,
  certified: Seq[(ClientAttribute, CertifiedTenantAttribute)],
  declared: Seq[(ClientAttribute, DeclaredTenantAttribute)],
  verified: Seq[(ClientAttribute, VerifiedTenantAttribute)],
  submitter: String,
  submissionTimestamp: OffsetDateTime,
  activator: String,
  activationTimestamp: OffsetDateTime
)
