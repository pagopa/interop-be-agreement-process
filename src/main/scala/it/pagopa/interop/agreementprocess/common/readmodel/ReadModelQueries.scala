package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.model.AgreementState
import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, count, project}
import org.mongodb.scala.model.{Filters}
import org.mongodb.scala.model.Projections.{computed, fields, include}
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._

object ReadModelQueries {
  def listAgreements(
    eServicesIds: List[String],
    consumersIds: List[String],
    producersIds: List[String],
    descriptorsIds: List[String],
    states: List[AgreementState],
    showOnlyUpgradeable: Boolean,
    offset: Int,
    limit: Int
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[PaginatedResult[PersistentAgreement]] = {

    val query: Bson =
      listAgreementsFilters(eServicesIds, consumersIds, producersIds, descriptorsIds, states, showOnlyUpgradeable)

    for {

      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        Seq(`match`(query), project(fields(include("data")))),
        offset = offset,
        limit = limit
      )
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        Seq(
          `match`(query),
          count("totalCount"),
          project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))
        ),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield PaginatedResult(results = agreements, totalCount = count.headOption.map(_.totalCount).getOrElse(0))

  }

  def listAgreementsFilters(
    eServicesIds: List[String],
    consumersIds: List[String],
    producersIds: List[String],
    descriptorsIds: List[String],
    states: List[AgreementState],
    showOnlyUpgradeable: Boolean
  ): Bson = {

    val statesFilter: Option[Bson] =
      if (showOnlyUpgradeable)
        listStatesFilter(List(AgreementState.DRAFT, AgreementState.ACTIVE, AgreementState.SUSPENDED))
      else
        listStatesFilter(states)

    // TBD
    val eservicesFilter      = Filters.empty()
    val eServicesIdsFilter   = mapToVarArgs(eServicesIds.map(Filters.eq("data.eserviceId", _)))(Filters.or)
    val consumersIdsFilter   = mapToVarArgs(consumersIds.map(Filters.eq("data.consumerId", _)))(Filters.or)
    val producersIdsFilter   = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)
    val descriptorsIdsFilter = mapToVarArgs(descriptorsIds.map(Filters.eq("data.descriptorId", _)))(Filters.or)

    mapToVarArgs(
      eServicesIdsFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter.toList ++ descriptorsIdsFilter.toList ++ statesFilter.toList :+ eservicesFilter
    )(Filters.and).getOrElse(Filters.empty())
  }

  def listStatesFilter(states: List[AgreementState]): Option[Bson] =
    mapToVarArgs(
      states
        .map(_.toPersistentApi)
        .map(_.toString)
        .map(Filters.eq("data.state", _))
    )(Filters.or)

  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))

}
