package it.pagopa.interop.agreementprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import spray.json._

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit def verifiedAttributeJsonFormat: RootJsonFormat[VerifiedAttribute]   = jsonFormat1(VerifiedAttribute)
  implicit def declaredAttributeJsonFormat: RootJsonFormat[DeclaredAttribute]   = jsonFormat1(DeclaredAttribute)
  implicit def certifiedAttributeJsonFormat: RootJsonFormat[CertifiedAttribute] = jsonFormat1(CertifiedAttribute)
  implicit def documentSeedJsonFormat: RootJsonFormat[DocumentSeed]             = jsonFormat5(DocumentSeed)
  implicit def documentJsonFormat: RootJsonFormat[Document]                     = jsonFormat6(Document)
  implicit def agreementJsonFormat: RootJsonFormat[Agreement]                   = jsonFormat19(Agreement)
  implicit def agreementPayloadJsonFormat: RootJsonFormat[AgreementPayload]     = jsonFormat2(AgreementPayload)
  implicit def agreementUpdatePayloadJsonFormat: RootJsonFormat[AgreementUpdatePayload]         =
    jsonFormat1(AgreementUpdatePayload)
  implicit def agreementSubmissionPayloadJsonFormat: RootJsonFormat[AgreementSubmissionPayload] =
    jsonFormat1(AgreementSubmissionPayload)
  implicit def agreementRejectionPayloadJsonFormat: RootJsonFormat[AgreementRejectionPayload]   =
    jsonFormat1(AgreementRejectionPayload)
  implicit def problemErrorFormat: RootJsonFormat[ProblemError]                  = jsonFormat2(ProblemError)
  implicit def problemFormat: RootJsonFormat[Problem]                            = jsonFormat6(Problem)
  implicit def agreementsFormat: RootJsonFormat[Agreements]                      = jsonFormat2(Agreements)
  implicit def compactOrganizationFormat: RootJsonFormat[CompactOrganization]    = jsonFormat2(CompactOrganization)
  implicit def compactOrganizationsFormat: RootJsonFormat[CompactOrganizations]  = jsonFormat2(CompactOrganizations)
  implicit def agreementCompactEServiceFormat: RootJsonFormat[CompactEService]   = jsonFormat2(CompactEService)
  implicit def agreementCompactEServicesFormat: RootJsonFormat[CompactEServices] = jsonFormat2(CompactEServices)
  implicit def TenantVerifierFormat: RootJsonFormat[TenantVerifier]              = jsonFormat4(TenantVerifier)
  implicit def TenantRevokerFormat: RootJsonFormat[TenantRevoker]                = jsonFormat5(TenantRevoker)
  implicit def DeclaredTenantAttributeFormat: RootJsonFormat[DeclaredTenantAttribute]   =
    jsonFormat3(DeclaredTenantAttribute)
  implicit def CertifiedTenantAttributeFormat: RootJsonFormat[CertifiedTenantAttribute] =
    jsonFormat3(CertifiedTenantAttribute)
  implicit def VerifiedTenantAttributeFormat: RootJsonFormat[VerifiedTenantAttribute]   =
    jsonFormat4(VerifiedTenantAttribute)
  implicit def tenantAttributeFormat: RootJsonFormat[TenantAttribute]                   = jsonFormat3(TenantAttribute)
  implicit def compactTenantFormat: RootJsonFormat[CompactTenant]                       = jsonFormat2(CompactTenant)
  implicit def mailInfoFormat: RootJsonFormat[MailTemplate] = jsonFormat2(MailTemplate.apply)
  implicit def computeAgreementPayloadFormat: RootJsonFormat[ComputeAgreementStatePayload] =
    jsonFormat2(ComputeAgreementStatePayload)

}
