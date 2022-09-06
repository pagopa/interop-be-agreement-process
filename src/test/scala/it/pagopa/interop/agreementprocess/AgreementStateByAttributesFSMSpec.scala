package it.pagopa.interop.agreementprocess

import org.scalatest.wordspec.AnyWordSpecLike

object AgreementStateByAttributesFSMSpec extends AnyWordSpecLike {

  "from DRAFT" should {
    "go to PENDING when Certified and Declared attributes are satisfied" in {}
    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {}
  }

  "from PENDING" should {
    "go to ACTIVE when Certified, Declared and Verified attributes are satisfied" in {}
    "go to PENDING when Verified attributes are NOT satisfied" in {}
    "go to DRAFT when Declared attributes are NOT satisfied" in {}
    "go to MISSING_CERTIFIED_ATTRIBUTES when Certified attributes are NOT satisfied" in {}
  }

  "from ACTIVE" should {
    "go to SUSPENDED when Certified attributes are NOT satisfied" in {}
    "go to SUSPENDED when Declared attributes are NOT satisfied" in {}
    "go to SUSPENDED when Verified attributes are NOT satisfied" in {}
  }

  "from SUSPENDED" should {
    "go to SUSPENDED when Certified, Declared and Verified attributes are satisfied" in {}
    "go to SUSPENDED when Certified attributes are NOT satisfied" in {}
    "go to SUSPENDED when Declared attributes are NOT satisfied" in {}
    "go to SUSPENDED when Verified attributes are NOT satisfied" in {}
  }

  "Certified attributes check" should {
    "return true if all EService single attributes are satisfied" in {}
    "return true if at least one attribute in every EService group attribute is satisfied" in {}
    "return false if at least one EService single attribute is not satisfied" in {}
    "return false if at least one EService group attribute is not satisfied" in {}
  }

  "Declared attributes check" should {
    "return true if all EService single attributes are satisfied" in {}
    "return true if at least one attribute in every EService group attribute is satisfied" in {}
    "return false if at least one EService single attribute is not satisfied" in {}
    "return false if at least one EService group attribute is not satisfied" in {}
  }

  "Verified attributes check" should {
    "return true if all EService single attributes are satisfied" in {}
    "return true if at least one attribute in every EService group attribute is satisfied" in {}
    "return false if at least one EService single attribute is not satisfied" in {}
    "return false if at least one EService group attribute is not satisfied" in {}
  }
}
