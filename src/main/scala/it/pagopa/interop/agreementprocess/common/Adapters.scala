package it.pagopa.interop.agreementprocess.common

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{
  AgreementNotInExpectedState,
  InvalidAttributeStructure
}
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementmanagement.model.agreement._
import it.pagopa.interop.tenantmanagement.model.{tenant => TenantManagement}

import java.util.UUID

object Adapters {

  val ACTIVABLE_STATES: Set[PersistentAgreementState]   =
    Set(Pending, Suspended)
  val SUSPENDABLE_STATES: Set[PersistentAgreementState] =
    Set(Active, Suspended)
  val ARCHIVABLE_STATES: Set[PersistentAgreementState]  =
    Set(Active, Suspended)
  val SUBMITTABLE_STATES: Set[PersistentAgreementState] = Set(Draft)
  val UPDATEABLE_STATES: Set[PersistentAgreementState]  =
    Set(Draft)
  val UPGRADABLE_STATES: Set[PersistentAgreementState]  =
    Set(Active, Suspended)
  val REJECTABLE_STATES: Set[PersistentAgreementState]  = Set(Pending)
  val DELETABLE_STATES: Set[PersistentAgreementState]   =
    Set(Draft, MissingCertifiedAttributes)

  implicit class AgreementWrapper(private val a: AgreementManagement.Agreement) extends AnyVal {
    def toApi: Agreement                          = Agreement(
      id = a.id,
      eserviceId = a.eserviceId,
      descriptorId = a.descriptorId,
      producerId = a.producerId,
      consumerId = a.consumerId,
      state = a.state.toApi,
      verifiedAttributes = a.verifiedAttributes.map(_.toApi),
      certifiedAttributes = a.certifiedAttributes.map(_.toApi),
      declaredAttributes = a.declaredAttributes.map(_.toApi),
      suspendedByConsumer = a.suspendedByConsumer,
      suspendedByProducer = a.suspendedByProducer,
      suspendedByPlatform = a.suspendedByPlatform,
      consumerDocuments = a.consumerDocuments.map(_.toApi),
      consumerNotes = a.consumerNotes,
      rejectionReason = a.rejectionReason,
      createdAt = a.createdAt,
      updatedAt = a.updatedAt,
      contract = a.contract.map(_.toApi)
    )
    def toSeed: AgreementManagement.AgreementSeed = AgreementManagement.AgreementSeed(
      eserviceId = a.eserviceId,
      descriptorId = a.descriptorId,
      producerId = a.producerId,
      consumerId = a.consumerId,
      verifiedAttributes = Nil,
      certifiedAttributes = Nil,
      declaredAttributes = Nil,
      consumerNotes = a.consumerNotes
    )
    def toPersistent: PersistentAgreement         = PersistentAgreement(
      id = a.id,
      eserviceId = a.eserviceId,
      descriptorId = a.descriptorId,
      producerId = a.producerId,
      consumerId = a.consumerId,
      state = a.state.toPersistent,
      verifiedAttributes = a.verifiedAttributes.map(_.toPersistent),
      certifiedAttributes = a.certifiedAttributes.map(_.toPersistent),
      declaredAttributes = a.declaredAttributes.map(_.toPersistent),
      suspendedByConsumer = a.suspendedByConsumer,
      suspendedByProducer = a.suspendedByProducer,
      suspendedByPlatform = a.suspendedByPlatform,
      consumerNotes = a.consumerNotes,
      rejectionReason = a.rejectionReason,
      consumerDocuments = a.consumerDocuments.map(_.toPersistent),
      createdAt = a.createdAt,
      updatedAt = a.updatedAt,
      contract = a.contract.map(_.toPersistent),
      stamps = a.stamps.toPersistent,
      suspendedAt = a.suspendedAt
    )
  }

  implicit class AgreementManagementStateWrapper(private val s: AgreementManagement.AgreementState) extends AnyVal {
    def toApi: AgreementState                  = s match {
      case AgreementManagement.AgreementState.DRAFT                        => AgreementState.DRAFT
      case AgreementManagement.AgreementState.PENDING                      => AgreementState.PENDING
      case AgreementManagement.AgreementState.ACTIVE                       => AgreementState.ACTIVE
      case AgreementManagement.AgreementState.SUSPENDED                    => AgreementState.SUSPENDED
      case AgreementManagement.AgreementState.ARCHIVED                     => AgreementState.ARCHIVED
      case AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
        AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case AgreementManagement.AgreementState.REJECTED                     => AgreementState.REJECTED
    }
    def toPersistent: PersistentAgreementState = s match {
      case AgreementManagement.AgreementState.DRAFT                        => Draft
      case AgreementManagement.AgreementState.PENDING                      => Pending
      case AgreementManagement.AgreementState.ACTIVE                       => Active
      case AgreementManagement.AgreementState.SUSPENDED                    => Suspended
      case AgreementManagement.AgreementState.ARCHIVED                     => Archived
      case AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES => MissingCertifiedAttributes
      case AgreementManagement.AgreementState.REJECTED                     => Rejected
    }
  }
  implicit class AgreementStateWrapper(private val s: AgreementState)                               extends AnyVal {

    def toPersistent: PersistentAgreementState = s match {
      case AgreementState.DRAFT                        => Draft
      case AgreementState.PENDING                      => Pending
      case AgreementState.ACTIVE                       => Active
      case AgreementState.SUSPENDED                    => Suspended
      case AgreementState.ARCHIVED                     => Archived
      case AgreementState.MISSING_CERTIFIED_ATTRIBUTES => MissingCertifiedAttributes
      case AgreementState.REJECTED                     => Rejected
    }
  }

  implicit class VerifiedAttributeWrapper(private val a: AgreementManagement.VerifiedAttribute) extends AnyVal {
    def toApi: VerifiedAttribute                  = VerifiedAttribute(id = a.id)
    def toPersistent: PersistentVerifiedAttribute = PersistentVerifiedAttribute(id = a.id)
  }

  implicit class CertifiedAttributeWrapper(private val a: AgreementManagement.CertifiedAttribute) extends AnyVal {
    def toApi: CertifiedAttribute                  = CertifiedAttribute(id = a.id)
    def toPersistent: PersistentCertifiedAttribute = PersistentCertifiedAttribute(id = a.id)
  }

  implicit class DeclaredAttributeWrapper(private val a: AgreementManagement.DeclaredAttribute) extends AnyVal {
    def toApi: DeclaredAttribute                  = DeclaredAttribute(id = a.id)
    def toPersistent: PersistentDeclaredAttribute = PersistentDeclaredAttribute(id = a.id)
  }

  implicit class DocumentSeedWrapper(private val d: AgreementManagement.DocumentSeed) extends AnyVal {
    def toApi: DocumentSeed =
      DocumentSeed(id = d.id, name = d.name, prettyName = d.prettyName, contentType = d.contentType, path = d.path)
  }

  implicit class DocumentWrapper(private val d: AgreementManagement.Document) extends AnyVal {
    def toApi: Document                                                        = Document(
      id = d.id,
      name = d.name,
      prettyName = d.prettyName,
      contentType = d.contentType,
      createdAt = d.createdAt,
      path = d.path
    )
    def toSeed(newId: UUID, newPath: String): AgreementManagement.DocumentSeed = AgreementManagement.DocumentSeed(
      id = newId,
      name = d.name,
      prettyName = d.prettyName,
      contentType = d.contentType,
      path = newPath
    )
    def toPersistent: PersistentAgreementDocument                              = PersistentAgreementDocument(
      id = d.id,
      name = d.name,
      prettyName = d.prettyName,
      contentType = d.contentType,
      createdAt = d.createdAt,
      path = d.path
    )
  }

  implicit class AgreementPayloadWrapper(private val p: AgreementPayload) extends AnyVal {
    def toSeed(producerId: UUID, consumerId: UUID): AgreementManagement.AgreementSeed =
      AgreementManagement.AgreementSeed(
        eserviceId = p.eserviceId,
        descriptorId = p.descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Nil,
        certifiedAttributes = Nil,
        declaredAttributes = Nil
      )
  }
  implicit class StampsWrapper(private val p: AgreementManagement.Stamps) extends AnyVal {
    def toPersistent: PersistentStamps = PersistentStamps(
      submission = p.submission.map(_.toPersistent),
      activation = p.activation.map(_.toPersistent),
      rejection = p.rejection.map(_.toPersistent),
      suspensionByProducer = p.suspensionByProducer.map(_.toPersistent),
      suspensionByConsumer = p.suspensionByConsumer.map(_.toPersistent),
      upgrade = p.upgrade.map(_.toPersistent),
      archiving = p.archiving.map(_.toPersistent)
    )
  }

  implicit class StampWrapper(private val s: AgreementManagement.Stamp)                           extends AnyVal {
    def toPersistent: PersistentStamp = PersistentStamp(who = s.who, when = s.when)
  }
  implicit class PersistentAgreementWrapper(private val p: PersistentAgreement)                   extends AnyVal {
    def assertSubmittableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(SUBMITTABLE_STATES.contains(p.state))

    def assertActivableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(ACTIVABLE_STATES.contains(p.state))

    def assertSuspendableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(SUSPENDABLE_STATES.contains(p.state))

    def assertArchivableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(ARCHIVABLE_STATES.contains(p.state))

    def assertUpdateableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(UPDATEABLE_STATES.contains(p.state))

    def assertUpgradableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(UPGRADABLE_STATES.contains(p.state))

    def assertRejectableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(REJECTABLE_STATES.contains(p.state))

    def assertDeletableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(p.id.toString, p.state))
      .withRight[Unit]
      .unlessA(DELETABLE_STATES.contains(p.state))

    def toApi: Agreement                            = Agreement(
      id = p.id,
      eserviceId = p.eserviceId,
      descriptorId = p.descriptorId,
      producerId = p.producerId,
      consumerId = p.consumerId,
      state = p.state.toApi,
      verifiedAttributes = p.verifiedAttributes.map(_.toApi),
      certifiedAttributes = p.certifiedAttributes.map(_.toApi),
      declaredAttributes = p.declaredAttributes.map(_.toApi),
      suspendedByConsumer = p.suspendedByConsumer,
      suspendedByProducer = p.suspendedByProducer,
      suspendedByPlatform = p.suspendedByPlatform,
      consumerNotes = p.consumerNotes,
      rejectionReason = p.rejectionReason,
      consumerDocuments = p.consumerDocuments.map(_.toApi),
      createdAt = p.createdAt,
      updatedAt = p.updatedAt,
      contract = p.contract.map(_.toApi)
    )
    def toManagement: AgreementManagement.Agreement = AgreementManagement.Agreement(
      id = p.id,
      eserviceId = p.eserviceId,
      descriptorId = p.descriptorId,
      producerId = p.producerId,
      consumerId = p.consumerId,
      state = p.state.toManagement,
      verifiedAttributes = p.verifiedAttributes.map(_.toManagement),
      certifiedAttributes = p.certifiedAttributes.map(_.toManagement),
      declaredAttributes = p.declaredAttributes.map(_.toManagement),
      suspendedByConsumer = p.suspendedByConsumer,
      suspendedByProducer = p.suspendedByProducer,
      suspendedByPlatform = p.suspendedByPlatform,
      consumerNotes = p.consumerNotes,
      rejectionReason = p.rejectionReason,
      consumerDocuments = p.consumerDocuments.map(_.toManagement),
      createdAt = p.createdAt,
      updatedAt = p.updatedAt,
      contract = p.contract.map(_.toManagement),
      stamps = p.stamps.toManagement
    )
    def toSeed: AgreementManagement.AgreementSeed   = AgreementManagement.AgreementSeed(
      eserviceId = p.eserviceId,
      descriptorId = p.descriptorId,
      producerId = p.producerId,
      consumerId = p.consumerId,
      verifiedAttributes = Nil,
      certifiedAttributes = Nil,
      declaredAttributes = Nil,
      consumerNotes = p.consumerNotes
    )
  }
  implicit class PersistentAgreementStateWrapper(private val p: PersistentAgreementState)         extends AnyVal {
    def toApi: AgreementState                            = p match {
      case Draft                      => AgreementState.DRAFT
      case Active                     => AgreementState.ACTIVE
      case Archived                   => AgreementState.ARCHIVED
      case MissingCertifiedAttributes => AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case Pending                    => AgreementState.PENDING
      case Rejected                   => AgreementState.REJECTED
      case Suspended                  => AgreementState.SUSPENDED
    }
    def toManagement: AgreementManagement.AgreementState = p match {
      case Draft                      => AgreementManagement.AgreementState.DRAFT
      case Active                     => AgreementManagement.AgreementState.ACTIVE
      case Archived                   => AgreementManagement.AgreementState.ARCHIVED
      case MissingCertifiedAttributes => AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case Pending                    => AgreementManagement.AgreementState.PENDING
      case Rejected                   => AgreementManagement.AgreementState.REJECTED
      case Suspended                  => AgreementManagement.AgreementState.SUSPENDED
    }
  }
  implicit class PersistentVerifiedAttributeWrapper(private val p: PersistentVerifiedAttribute)   extends AnyVal {
    def toApi: VerifiedAttribute                            = VerifiedAttribute(id = p.id)
    def toManagement: AgreementManagement.VerifiedAttribute = AgreementManagement.VerifiedAttribute(id = p.id)
  }
  implicit class PersistentCertifiedAttributeWrapper(private val p: PersistentCertifiedAttribute) extends AnyVal {
    def toApi: CertifiedAttribute                            = CertifiedAttribute(id = p.id)
    def toManagement: AgreementManagement.CertifiedAttribute = AgreementManagement.CertifiedAttribute(id = p.id)
  }
  implicit class PersistentDeclaredAttributeWrapper(private val p: PersistentDeclaredAttribute)   extends AnyVal {
    def toApi: DeclaredAttribute                            = DeclaredAttribute(id = p.id)
    def toManagement: AgreementManagement.DeclaredAttribute = AgreementManagement.DeclaredAttribute(id = p.id)
  }
  implicit class PersistentStampsWrapper(private val p: PersistentStamps)                         extends AnyVal {
    def toManagement: AgreementManagement.Stamps = AgreementManagement.Stamps(
      submission = p.submission.map(_.toManagement),
      activation = p.activation.map(_.toManagement),
      rejection = p.rejection.map(_.toManagement),
      suspensionByProducer = p.suspensionByProducer.map(_.toManagement),
      suspensionByConsumer = p.suspensionByConsumer.map(_.toManagement),
      upgrade = p.upgrade.map(_.toManagement),
      archiving = p.archiving.map(_.toManagement)
    )
  }
  implicit class PersistentStampWrapper(private val p: PersistentStamp)                           extends AnyVal {
    def toManagement: AgreementManagement.Stamp = AgreementManagement.Stamp(who = p.who, when = p.when)
  }
  implicit class PersistentAgreementDocumentWrapper(private val p: PersistentAgreementDocument)   extends AnyVal {
    def toApi: Document                                                        = Document(
      id = p.id,
      name = p.name,
      prettyName = p.prettyName,
      contentType = p.contentType,
      createdAt = p.createdAt,
      path = p.path
    )
    def toManagement: AgreementManagement.Document                             = AgreementManagement.Document(
      id = p.id,
      name = p.name,
      prettyName = p.prettyName,
      contentType = p.contentType,
      createdAt = p.createdAt,
      path = p.path
    )
    def toSeed(newId: UUID, newPath: String): AgreementManagement.DocumentSeed = AgreementManagement.DocumentSeed(
      id = newId,
      name = p.name,
      prettyName = p.prettyName,
      contentType = p.contentType,
      path = newPath
    )
  }

  implicit class PersistentVerificationTenantVerifierObjectWrapper(
    private val p: TenantManagement.PersistentTenantVerifier.type
  ) extends AnyVal {
    def fromAPI(p: TenantVerifier): TenantManagement.PersistentTenantVerifier =
      TenantManagement.PersistentTenantVerifier(
        id = p.id,
        verificationDate = p.verificationDate,
        expirationDate = p.expirationDate,
        extensionDate = p.expirationDate
      )
  }

  implicit class PersistentVerificationTenantRevokerObjectWrapper(
    private val p: TenantManagement.PersistentTenantRevoker.type
  ) extends AnyVal {
    def fromAPI(p: TenantRevoker): TenantManagement.PersistentTenantRevoker = TenantManagement.PersistentTenantRevoker(
      id = p.id,
      verificationDate = p.verificationDate,
      expirationDate = p.expirationDate,
      extensionDate = p.expirationDate,
      revocationDate = p.revocationDate
    )
  }

  implicit class PersistentAttributesObjectWrapper(private val p: TenantManagement.PersistentTenantAttribute.type)
      extends AnyVal {
    def fromAPI(attribute: TenantAttribute): Either[Throwable, TenantManagement.PersistentTenantAttribute] =
      attribute match {
        case TenantAttribute(Some(declared), None, None)  =>
          TenantManagement
            .PersistentDeclaredAttribute(declared.id, declared.assignmentTimestamp, declared.revocationTimestamp)
            .asRight
        case TenantAttribute(None, Some(certified), None) =>
          TenantManagement
            .PersistentCertifiedAttribute(certified.id, certified.assignmentTimestamp, certified.revocationTimestamp)
            .asRight
        case TenantAttribute(None, None, Some(verified))  =>
          TenantManagement
            .PersistentVerifiedAttribute(
              id = verified.id,
              assignmentTimestamp = verified.assignmentTimestamp,
              verifiedBy = verified.verifiedBy.toList.map(TenantManagement.PersistentTenantVerifier.fromAPI),
              revokedBy = verified.revokedBy.toList.map(TenantManagement.PersistentTenantRevoker.fromAPI)
            )
            .asRight
        case _                                            => InvalidAttributeStructure.asLeft
      }
  }
}
