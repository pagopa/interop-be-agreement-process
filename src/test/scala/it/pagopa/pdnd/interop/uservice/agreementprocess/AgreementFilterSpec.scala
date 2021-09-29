package it.pagopa.pdnd.interop.uservice.agreementprocess

import it.pagopa.pdnd.interop.uservice.agreementprocess.api.impl.{AgreementFilter, DescriptorVersion}
import it.pagopa.pdnd.interop.uservice.agreementprocess.model.{Agreement, EService, Organization}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class AgreementFilterSpec extends AnyWordSpecLike with Matchers with ScalaFutures {

  private def getTestData: Seq[(Agreement, DescriptorVersion)] = {
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

    val eservice1 = EService(UUID.randomUUID(), "eservice1", "eservice1")
    val eservice2 = EService(UUID.randomUUID(), "eservice2", "eservice2")
    val eservice3 = EService(UUID.randomUUID(), "eservice3", "eservice3")
    val eservice4 = EService(UUID.randomUUID(), "eservice4", "eservice4")

    Seq(
      (
        Agreement(
          id = uuid1,
          producer = producerOrg1,
          consumer = consumerOrg1,
          eservice = eservice1,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(1)
      ),
      (
        Agreement(
          id = uuid2,
          producer = producerOrg2,
          consumer = consumerOrg2,
          eservice = eservice2,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(2)
      ),
      (
        Agreement(
          id = uuid3,
          producer = producerOrg3,
          consumer = consumerOrg3,
          eservice = eservice3,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(3)
      ),
      (
        Agreement(
          id = uuid4,
          producer = producerOrg4,
          consumer = consumerOrg4,
          eservice = eservice4,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(4)
      )
    )
  }

  "an Agreement filter" should {
    "return the same sequence when no latest filter is applied" in {
      //given
      val agreements = getTestData

      //when
      val filtered = AgreementFilter.filterAgreementsByLatestVersion(None, agreements)

      //then
      val expectedId = agreements.map(_._1.id)
      filtered.futureValue.map(_.id) should contain only (expectedId: _*)
    }

    "return the same sequence when FALSE latest filter is applied" in {
      //given
      val agreements = getTestData

      //when
      val filtered = AgreementFilter.filterAgreementsByLatestVersion(Some(false), agreements)

      //then
      val expectedId = agreements.map(_._1.id)
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

    val eservice1 = EService(UUID.randomUUID(), "eservice1", "eservice1")
    val eservice2 = EService(UUID.randomUUID(), "eservice2", "eservice2")

    val agreementsToFilter = Seq(
      (
        Agreement(
          id = uuid1,
          producer = producerOrg1,
          consumer = consumerOrg1,
          eservice = eservice1,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(100L)
      ),
      (
        Agreement(
          id = uuid2,
          producer = producerOrg2,
          consumer = consumerOrg2,
          eservice = eservice1,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(2L)
      ),
      (
        Agreement(
          id = uuid3,
          producer = producerOrg3,
          consumer = consumerOrg3,
          eservice = eservice2,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(30L)
      ),
      (
        Agreement(
          id = uuid4,
          producer = producerOrg4,
          consumer = consumerOrg4,
          eservice = eservice2,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(4L)
      )
    )

    //when
    val filtered = AgreementFilter.filterAgreementsByLatestVersion(Some(true), agreementsToFilter)

    //then
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

    val eservice1 = EService(UUID.randomUUID(), "eservice1", "eservice1")
    val eservice2 = EService(UUID.randomUUID(), "eservice2", "eservice2")

    val agreementsToFilter = Seq(
      (
        Agreement(
          id = uuid1,
          producer = producerOrg1,
          consumer = consumerOrg1,
          eservice = eservice1,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(100L)
      ),
      (
        Agreement(
          id = uuid2,
          producer = producerOrg2,
          consumer = consumerOrg2,
          eservice = eservice1,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(2L)
      ),
      (
        Agreement(
          id = uuid3,
          producer = producerOrg3,
          consumer = consumerOrg3,
          eservice = eservice2,
          status = "Active",
          attributes = Seq.empty
        ),
        None
      ),
      (
        Agreement(
          id = uuid4,
          producer = producerOrg4,
          consumer = consumerOrg4,
          eservice = eservice2,
          status = "Active",
          attributes = Seq.empty
        ),
        Some(4L)
      )
    )

    //when
    val filtered = AgreementFilter.filterAgreementsByLatestVersion(Some(true), agreementsToFilter)

    //then
    filtered.futureValue.map(_.id) should contain only (uuid1, uuid4)
  }
}
