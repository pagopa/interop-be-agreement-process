package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.model.AgreementState
import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, count, lookup, project, sort, unwind, addFields}
import org.mongodb.scala.model.{Filters, Field}
import org.mongodb.scala.model.Projections.{computed, fields, include}
import org.mongodb.scala.model.Sorts.ascending
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}

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

    val eServicesLookupPipeline: Seq[Bson] =
      Seq(`match`(query), lookup("eservices", "data.eserviceId", "data.id", "eservices"))

    val upgradablePipeline: Seq[Bson] = {
      if (showOnlyUpgradeable) {
        Seq(
          unwind(fieldName = "eservices.data.descriptors"),
          addFields(
            Field(
              "currentDescriptor",
              Document("""{ $filter: {
              input: "$eservices.data.descriptors",
              as: "descr",         
              cond: {  $eq: ["$$descr.id" , "$data.descriptorId"]}}} }""")
            )
          ),
          unwind(fieldName = "$currentDescriptor"),
          addFields(
            Field(
              "upgradableDescriptor",
              Document(
                """{ $filter: {
              input: "$eservices.data.descriptors",
              as: "upgradable",         
              cond: { $and: [
                      {$gt:[ {"$toInt" :  {"$toInt" : "$$upgradable.version"}} , {"$toInt": {"$toInt": "$currentDescriptor.version"}}]}, 
                      {$in:["$$upgradable.state", ['""" + CatalogManagement.Published + """', '""" +
                  CatalogManagement.Suspended + """']]}
                  ]}}} }"""
              )
            )
          ),
          `match`(Filters.exists("upgradableDescriptor.0", true))
        )

      } else
        Seq.empty
    }

    for {
      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        eServicesLookupPipeline ++ upgradablePipeline ++
          Seq(
            project(
              fields(include("data"), computed("lowerName", Document("""{ "$toLower" : "$eservices.data.name" }""")))
            ),
            sort(ascending("lowerName"))
          ),
        offset = offset,
        limit = limit
      )
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        if (upgradablePipeline.nonEmpty) eServicesLookupPipeline ++ upgradablePipeline
        else
          Nil ++ Seq(
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

    val upgreadableStates = (List(AgreementState.DRAFT, AgreementState.ACTIVE, AgreementState.SUSPENDED))
    val statesForFilter   = states match {
      case _ if showOnlyUpgradeable => upgreadableStates
      case other                    => other
    }

    val statesFilter = listStatesFilter(statesForFilter)

    val eServicesIdsFilter   = mapToVarArgs(eServicesIds.map(Filters.eq("data.eserviceId", _)))(Filters.or)
    val consumersIdsFilter   = mapToVarArgs(consumersIds.map(Filters.eq("data.consumerId", _)))(Filters.or)
    val producersIdsFilter   = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)
    val descriptorsIdsFilter = mapToVarArgs(descriptorsIds.map(Filters.eq("data.descriptorId", _)))(Filters.or)

    mapToVarArgs(
      eServicesIdsFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter.toList ++ descriptorsIdsFilter.toList ++ statesFilter.toList
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
