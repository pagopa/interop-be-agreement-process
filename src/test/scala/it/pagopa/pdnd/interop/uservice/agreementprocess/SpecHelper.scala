package it.pagopa.pdnd.interop.uservice.agreementprocess

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  AgreementEnums,
  VerifiedAttribute,
  Agreement => ClientAgreement
}

import java.util.UUID

trait SpecHelper {

  object Common {
    val consumerId: UUID   = UUID.fromString("07f8dce0-0a5b-476b-9fdd-a7a658eb9213")
    val attributiId1: UUID = UUID.fromString("07f8dce0-0a5b-476b-9fdd-a7a658eb9214")
    val attributiId2: UUID = UUID.fromString("07f8dce0-0a5b-476b-9fdd-a7a658eb9215")
    val attributiId3: UUID = UUID.fromString("07f8dce0-0a5b-476b-9fdd-a7a658eb9216")
  }

  object AgreementOne {
    val id: UUID         = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("17f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = Common.consumerId,
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(id = Common.attributiId1, verified = true, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId2, verified = true, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId3, verified = true, verificationDate = None, validityTimespan = None)
      )
    )
  }

  object AgreementTwo {
    val id: UUID         = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = Common.consumerId,
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(id = Common.attributiId1, verified = true, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId3, verified = true, verificationDate = None, validityTimespan = None)
      )
    )
  }

  object AgreementThree {
    val id: UUID         = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("37f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = Common.consumerId,
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(id = Common.attributiId1, verified = false, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId2, verified = false, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId3, verified = false, verificationDate = None, validityTimespan = None)
      )
    )
  }

  object AgreementFour {
    val id: UUID         = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9210")
    val eserviceId: UUID = UUID.fromString("47f8dce0-0a5b-476b-9fdd-a7a658eb9211")
    val producerId: UUID = UUID.fromString("4f8dce0-0a5b-476b-9fdd-a7a658eb9212")

    val agreement: ClientAgreement = ClientAgreement(
      id = id,
      eserviceId = eserviceId,
      producerId = producerId,
      consumerId = Common.consumerId,
      status = AgreementEnums.Status.Active,
      verifiedAttributes = Seq(
        VerifiedAttribute(id = Common.attributiId1, verified = true, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId2, verified = false, verificationDate = None, validityTimespan = None),
        VerifiedAttribute(id = Common.attributiId3, verified = true, verificationDate = None, validityTimespan = None)
      )
    )
  }

  val agreementsAllTrue: Seq[ClientAgreement]        = Seq(AgreementOne.agreement, AgreementTwo.agreement)
  val agreementsAllFalse: Seq[ClientAgreement]       = Seq(AgreementThree.agreement)
  val agreementsSameTrueFalse: Seq[ClientAgreement]  = Seq(AgreementOne.agreement, AgreementThree.agreement)
  val agreementsExcludingFalse: Seq[ClientAgreement] = Seq(AgreementOne.agreement, AgreementFour.agreement)
}
