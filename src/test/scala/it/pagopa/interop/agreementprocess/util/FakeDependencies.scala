package it.pagopa.interop.agreementprocess.util

import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.model.AgreementPayload
import it.pagopa.interop.agreementprocess.service.{
  AgreementManagementService,
  AttributeManagementService,
  AuthorizationManagementService,
  CatalogManagementService,
  ClientAttribute,
  PartyManagementService
}
import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributeKind
import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.catalogmanagement.client.model.{Attributes, EService, EServiceTechnology}
import it.pagopa.interop.selfcare.partymanagement.client.model.{Attribute, Institution}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeAttributeManagementService extends AttributeManagementService {
    override def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
      Future.successful(
        attributeregistrymanagement.client.model.Attribute(
          /* uniquely identifies the attribute on the registry */
          id = "String",
          kind = AttributeKind.DECLARED,
          description = "fake",
          name = "fake",
          creationTime = OffsetDateTime.now()
        )
      )

    override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[ClientAttribute] =
      Future.successful(
        attributeregistrymanagement.client.model.Attribute(
          /* uniquely identifies the attribute on the registry */
          id = "String",
          kind = AttributeKind.DECLARED,
          description = "fake",
          name = "fake",
          creationTime = OffsetDateTime.now()
        )
      )
  }

  class FakePartyManagementService extends PartyManagementService {
    override def getPartyAttributes(
      partyId: UUID
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]] =
      Future.successful(Seq.empty)

    override def getInstitution(
      partyId: UUID
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] =
      Future.successful(
        Institution(
          id = UUID.randomUUID(),
          /* external institution id */
          externalId = "fake",
          /* origin institution id (e.g iPA code) */
          originId = "fake",
          description = "fake",
          digitalAddress = "fake",
          address = "fake",
          zipCode = "fake",
          /* institution tax code */
          taxCode = "fake",
          /* The origin form which the institution has been retrieved */
          origin = "fake",
          /* institution type */
          institutionType = "fake",
          attributes = Seq.empty[Attribute]
        )
      )
  }

  class FakeAgreementManagementService     extends AgreementManagementService     {
    override def createAgreement(
      producerId: UUID,
      agreementPayload: AgreementPayload,
      verifiedAttributeSeeds: Seq[VerifiedAttributeSeed]
    )(implicit contexts: Seq[(String, String)]): Future[Agreement] = Future.successful(
      Agreement(
        id = UUID.randomUUID(),
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        state = AgreementState.ACTIVE,
        /* set of the verified attributes belonging to this agreement, if any. */
        verifiedAttributes = Seq.empty[VerifiedAttribute],
        createdAt = OffsetDateTime.now()
      )
    )

    override def getAgreementById(agreementId: String)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
      Future.successful(
        Agreement(
          id = UUID.randomUUID(),
          eserviceId = UUID.randomUUID(),
          descriptorId = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          consumerId = UUID.randomUUID(),
          state = AgreementState.ACTIVE,
          /* set of the verified attributes belonging to this agreement, if any. */
          verifiedAttributes = Seq.empty[VerifiedAttribute],
          createdAt = OffsetDateTime.now()
        )
      )

    override def getAgreements(
      producerId: Option[String],
      consumerId: Option[String],
      eserviceId: Option[String],
      descriptorId: Option[String],
      state: Option[AgreementState]
    )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = Future.successful(Seq.empty)

    override def activateById(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(
      Agreement(
        id = UUID.randomUUID(),
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        state = AgreementState.ACTIVE,
        /* set of the verified attributes belonging to this agreement, if any. */
        verifiedAttributes = Seq.empty[VerifiedAttribute],
        createdAt = OffsetDateTime.now()
      )
    )

    override def suspendById(agreementId: String, stateChangeDetails: StateChangeDetails)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(
      Agreement(
        id = UUID.randomUUID(),
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        state = AgreementState.ACTIVE,
        /* set of the verified attributes belonging to this agreement, if any. */
        verifiedAttributes = Seq.empty[VerifiedAttribute],
        createdAt = OffsetDateTime.now()
      )
    )

    override def markVerifiedAttribute(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(
      Agreement(
        id = UUID.randomUUID(),
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        state = AgreementState.ACTIVE,
        /* set of the verified attributes belonging to this agreement, if any. */
        verifiedAttributes = Seq.empty[VerifiedAttribute],
        createdAt = OffsetDateTime.now()
      )
    )

    override def upgradeById(agreementId: UUID, agreementSeed: AgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(
      Agreement(
        id = UUID.randomUUID(),
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        state = AgreementState.ACTIVE,
        /* set of the verified attributes belonging to this agreement, if any. */
        verifiedAttributes = Seq.empty[VerifiedAttribute],
        createdAt = OffsetDateTime.now()
      )
    )
  }
  class FakeCatalogManagementService       extends CatalogManagementService       {
    override def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
      Future.successful(
        EService(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          name = "fake",
          description = "fake",
          technology = EServiceTechnology.REST,
          attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
          descriptors = Seq.empty
        )
      )
  }
  class FakeAuthorizationManagementService extends AuthorizationManagementService {
    override def updateStateOnClients(
      eServiceId: UUID,
      consumerId: UUID,
      agreementId: UUID,
      state: ClientComponentState
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.successful(())
  }

}
