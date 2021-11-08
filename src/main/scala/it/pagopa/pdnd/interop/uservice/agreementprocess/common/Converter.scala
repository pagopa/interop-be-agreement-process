package it.pagopa.pdnd.interop.uservice.agreementprocess.common

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model._

object Converter {
  def dependencyAgreementStatusToApi(status: AgreementManagementDependency.AgreementStatusEnum): AgreementStatusEnum =
    status match {
      case AgreementManagementDependency.ACTIVE    => ACTIVE
      case AgreementManagementDependency.PENDING   => PENDING
      case AgreementManagementDependency.SUSPENDED => SUSPENDED
      case AgreementManagementDependency.INACTIVE  => INACTIVE
    }
}
