package it.pagopa.interop.agreementprocess.service.util

import it.pagopa.interop.agreementprocess.service.ClientAttribute
import it.pagopa.interop.tenantmanagement.model.tenant.{
  PersistentCertifiedAttribute,
  PersistentDeclaredAttribute,
  PersistentVerifiedAttribute
}

import java.time.OffsetDateTime
import java.util.UUID

final case class PDFPayload(
  today: OffsetDateTime,
  agreementId: UUID,
  eService: String,
  producerName: String,
  producerOrigin: String,
  producerIPACode: String,
  consumerName: String,
  consumerOrigin: String,
  consumerIPACode: String,
  certified: Seq[(ClientAttribute, PersistentCertifiedAttribute)],
  declared: Seq[(ClientAttribute, PersistentDeclaredAttribute)],
  verified: Seq[(ClientAttribute, PersistentVerifiedAttribute)],
  submitter: String,
  submissionTimestamp: OffsetDateTime,
  activator: String,
  activationTimestamp: OffsetDateTime
)
