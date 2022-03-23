package it.pagopa.interop.agreementprocess

import it.pagopa.interop.agreementprocess.api.impl.AgreementFilter
import it.pagopa.interop.agreementprocess.model.{Agreement, AgreementState, EService, Organization}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class AgreementFilterSpec extends AnyWordSpecLike with Matchers with ScalaFutures {

  private def getTestData: Seq[Agreement] = {
    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    val uuid3 = UUID.randomUUID()
    val uuid4 = UUID.randomUUID()

    val producerOrg1 = Organization("a1", "a1")
    val producerOrg2 = Organization("b1", "b1")
    val producerOrg3 = Organization("c1", "c1")
    val producerOrg4 = Organization("d1", "d1")

    val consumerOrg1 = Organization("cons1", "cons1")
    val consumerOrg2 = Organization("cons2", "cons2")
    val consumerOrg3 = Organization("cons3", "cons3")
    val consumerOrg4 = Organization("cons4", "cons4")

    val eservice1 = EService(UUID.randomUUID(), "eservice1", "1", None)
    val eservice2 = EService(UUID.randomUUID(), "eservice2", "2", None)
    val eservice3 = EService(UUID.randomUUID(), "eservice3", "3", None)
    val eservice4 = EService(UUID.randomUUID(), "eservice4", "4", None)

    Seq(
      Agreement(
        id = uuid1,
        producer = producerOrg1,
        consumer = consumerOrg1,
        eservice = eservice1,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid2,
        producer = producerOrg2,
        consumer = consumerOrg2,
        eservice = eservice2,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid3,
        producer = producerOrg3,
        consumer = consumerOrg3,
        eservice = eservice3,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid4,
        producer = producerOrg4,
        consumer = consumerOrg4,
        eservice = eservice4,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      )
    )
  }

  "an Agreement filter" should {
    "return the same sequence when no latest filter is applied" in {
      // given
      val agreements = getTestData

      // when
      val filtered = AgreementFilter.filterAgreementsByLatestVersion(None, agreements)

      // then
      val expectedId = agreements.map(_.id)
      filtered.futureValue.map(_.id) should contain only (expectedId: _*)
    }

    "return the same sequence when FALSE latest filter is applied" in {
      // given
      val agreements = getTestData

      // when
      val filtered = AgreementFilter.filterAgreementsByLatestVersion(Some(false), agreements)

      // then
      val expectedId = agreements.map(_.id)
      filtered.futureValue.map(_.id) should contain only (expectedId: _*)
    }
  }

  "return only the latest version for each agreement when latest filter is TRUE" in {
    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    val uuid3 = UUID.randomUUID()
    val uuid4 = UUID.randomUUID()

    val producerOrg1 = Organization("a1", "a1")
    val producerOrg2 = Organization("b1", "b1")
    val producerOrg3 = Organization("c1", "c1")
    val producerOrg4 = Organization("d1", "d1")

    val consumerOrg1 = Organization("cons1", "cons1")
    val consumerOrg2 = Organization("cons2", "cons2")
    val consumerOrg3 = Organization("cons3", "cons3")
    val consumerOrg4 = Organization("cons4", "cons4")

    val eserviceId1 = UUID.randomUUID()
    val eserviceId2 = UUID.randomUUID()
    val eservice1   = EService(eserviceId1, "eservice1", "100", None)
    val eservice2   = EService(eserviceId1, "eservice2", "2", None)
    val eservice3   = EService(eserviceId2, "eservice2", "30", None)
    val eservice4   = EService(eserviceId2, "eservice2", "4", None)

    val agreementsToFilter = Seq(
      Agreement(
        id = uuid1,
        producer = producerOrg1,
        consumer = consumerOrg1,
        eservice = eservice1,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid2,
        producer = producerOrg2,
        consumer = consumerOrg2,
        eservice = eservice2,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid3,
        producer = producerOrg3,
        consumer = consumerOrg3,
        eservice = eservice3,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid4,
        producer = producerOrg4,
        consumer = consumerOrg4,
        eservice = eservice4,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      )
    )

    // when
    val filtered = AgreementFilter.filterAgreementsByLatestVersion(Some(true), agreementsToFilter)

    // then
    filtered.futureValue.map(_.id) should contain only (uuid1, uuid3)
  }

  "return only the latest version for each agreement when latest filter is TRUE and some agreement has wrong version values" in {
    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()
    val uuid3 = UUID.randomUUID()
    val uuid4 = UUID.randomUUID()

    val producerOrg1 = Organization("a1", "a1")
    val producerOrg2 = Organization("b1", "b1")
    val producerOrg3 = Organization("c1", "c1")
    val producerOrg4 = Organization("d1", "d1")

    val consumerOrg1 = Organization("cons1", "cons1")
    val consumerOrg2 = Organization("cons2", "cons2")
    val consumerOrg3 = Organization("cons3", "cons3")
    val consumerOrg4 = Organization("cons4", "cons4")

    val eserviceId1 = UUID.randomUUID()
    val eserviceId2 = UUID.randomUUID()
    val eservice1   = EService(eserviceId1, "eservice1", "100", None)
    val eservice2   = EService(eserviceId1, "eservice2", "2", None)
    val eservice3   = EService(eserviceId2, "eservice2", "pippo", None)
    val eservice4   = EService(eserviceId2, "eservice2", "4", None)

    val agreementsToFilter = Seq(
      Agreement(
        id = uuid1,
        producer = producerOrg1,
        consumer = consumerOrg1,
        eservice = eservice1,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid2,
        producer = producerOrg2,
        consumer = consumerOrg2,
        eservice = eservice2,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid3,
        producer = producerOrg3,
        consumer = consumerOrg3,
        eservice = eservice3,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      ),
      Agreement(
        id = uuid4,
        producer = producerOrg4,
        consumer = consumerOrg4,
        eservice = eservice4,
        state = AgreementState.ACTIVE,
        attributes = Seq.empty,
        suspendedByConsumer = None,
        suspendedByProducer = None,
        eserviceDescriptorId = UUID.randomUUID()
      )
    )

    // when
    val filtered = AgreementFilter.filterAgreementsByLatestVersion(Some(true), agreementsToFilter)

    // then
    filtered.futureValue.map(_.id) should contain only (uuid1, uuid4)
  }
}
