package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.model.AgreementState
import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, count, project, sort}
import org.mongodb.scala.model.{Filters}
import org.mongodb.scala.model.Projections.{computed, fields, include}
import org.mongodb.scala.model.Sorts.ascending
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._

object ReadModelQueries {
  def listAgreements(
    eServicesIds: List[String],
    consumersIds: List[String],
    producersIds: List[String],
    states: List[AgreementState],
    showOnlyUpgradeable: Boolean,
    offset: Int,
    limit: Int
  )(readModel: ReadModelService)(implicit ec: ExecutionContext): Future[PaginatedResult[PersistentAgreement]] = {

    val query: Bson = listAgreementsFilters(eServicesIds, consumersIds, producersIds, states, showOnlyUpgradeable)

    for {

      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        Seq(
          `match`(query),
          project(fields(include("data"), computed("lowerName", Document("""{ "$toLower" : "$data.name" }""")))),
          sort(ascending("lowerName"))
        ),
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
    states: List[AgreementState],
    showOnlyUpgradeable: Boolean
  ): Bson = {

    val onlyUpgradableFilter: Bson = if (showOnlyUpgradeable) Filters.empty() else Filters.empty()

    val statesPartialFilter: Seq[Bson] = states
      .map(_.toPersistentApi)
      .map(_.toString)
      .map(Filters.eq("data.state", _))

    val statesFilter       = mapToVarArgs(statesPartialFilter)(Filters.or)
    val eServicesIdsFilter = mapToVarArgs(eServicesIds.map(Filters.eq("data.eserviceId", _)))(Filters.or)
    val consumersIdsFilter = mapToVarArgs(consumersIds.map(Filters.eq("data.consumerId", _)))(Filters.or)
    val producersIdsFilter = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)

    mapToVarArgs(
      eServicesIdsFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter ++ statesFilter.toList :+ onlyUpgradableFilter
    )(Filters.and).getOrElse(Filters.empty())
  }

  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))

}
