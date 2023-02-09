package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.model.agreement.PersistentAgreement
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.model.AgreementState
import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Accumulators.first
import org.mongodb.scala.model.Aggregates.{`match`, count, lookup, project, sort, unwind, addFields, group}
import org.mongodb.scala.model.{Filters, Field, UnwindOptions}
import org.mongodb.scala.model.Projections.{computed, fields, include}
import org.mongodb.scala.model.Sorts.ascending
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.agreementprocess.common.readmodel.model._

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
      Seq(`match`(query), lookup("eservices", "data.eserviceId", "data.id", "eservices"), unwind("$eservices"))

    val upgradablePipeline: Seq[Bson] = {
      if (showOnlyUpgradeable) {
        Seq(
          addFields(
            Field(
              "currentDescriptor",
              Document("""{ $filter: {
              input: "$eservices.data.descriptors",
              as: "descr",         
              cond: {  $eq: ["$$descr.id" , "$data.descriptorId"]}}} }""")
            )
          ),
          unwind("$currentDescriptor"),
          addFields(
            Field(
              "upgradableDescriptor",
              Document(
                """{ $filter: {
              input: "$eservices.data.descriptors",
              as: "upgradable",         
              cond: { $and: [
                      {$gt:["$$upgradable.activatedAt", "$currentDescriptor.activatedAt"]}, 
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

    val countPipeline: Seq[Bson] = {
      Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }"""))))
    }

    for {
      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        eServicesLookupPipeline ++ upgradablePipeline ++
          Seq(
            project(
              fields(
                include("data", "eservices"),
                computed("lowerName", Document("""{ "$toLower" : "$eservices.data.name" }"""))
              )
            ),
            sort(ascending("lowerName"))
          ),
        offset = offset,
        limit = limit
      )
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        if (upgradablePipeline.nonEmpty) eServicesLookupPipeline ++ upgradablePipeline ++ countPipeline
        else
          Seq(`match`(query)) ++ countPipeline,
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
      case Nil if showOnlyUpgradeable  => upgreadableStates
      case list if showOnlyUpgradeable => upgreadableStates.intersect(list)
      case other                       => other
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

  private def listEServiceFilters(name: Option[String]): Bson = {
    val nameFilter = name.map(Filters.regex("eservices.data.name", _, "i"))

    mapToVarArgs(nameFilter.toList)(Filters.and).getOrElse(Filters.empty())
  }

  @scala.annotation.nowarn
  def listProducers(name: Option[String], offset: Int, limit: Int)(
    readModel: ReadModelService
  )(implicit ec: ExecutionContext): Future[PaginatedResult[CompactTenant]] = {
    val query: Bson               = listEServiceFilters(name)
    val filterPipeline: Seq[Bson] = Seq(
      lookup("eservices", "data.eserviceId", "data.id", "eservices"),
      unwind("$eservices", UnwindOptions().preserveNullAndEmptyArrays(false)),
      `match`(query),
      lookup("tenants", "data.producerId", "data.id", "tenants"),
      unwind("$tenants", UnwindOptions().preserveNullAndEmptyArrays(false)),
      addFields(
        Field("id", Document("""{ id: "$data.producerId" }""")),
        Field("name", Document("""{ name: "$tenants.data.name" }"""))
      ),
      group(
        Document("""{ _id: "$data.producerId" } """),
        first("id", Document("""{ "id": "$data.producerId" } """)),
        first("name", Document("""{ "name": "$tenants.data.name" } """))
      ),
      project(Document(""" { "data": { "id" : "$id", "name": "$name" }}""")),
      sort(ascending("lowerName"))
    )

    println(filterPipeline.map(_.toBsonDocument().toJson()))
    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      agreements <- readModel.aggregate[CompactTenant](
        "agreements",
        filterPipeline, // ++
        // Seq(project(Document(""" { "data": { "id" : "$id", "name": "$name" }}""")), sort(ascending("lowerName"))),
        offset = offset,
        limit = limit
      )
      // Note: This could be obtained using $facet function (avoiding to execute the query twice),
      //   but it is not supported by DocumentDB
      // count      <- readModel.aggregate[TotalCountResult](
      //   "agreements",
      //   filterPipeline ++
      //     Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))),
      //   offset = 0,
      //   limit = Int.MaxValue
      // )
    } yield PaginatedResult(results = agreements, totalCount = 0) // count.headOption.map(_.totalCount).getOrElse(0))
  }

  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))

}
