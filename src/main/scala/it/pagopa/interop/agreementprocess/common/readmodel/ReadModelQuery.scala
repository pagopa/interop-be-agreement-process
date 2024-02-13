package it.pagopa.interop.agreementprocess.common.readmodel

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters

trait ReadModelQuery {
  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))

  def escape(str: String): String = str.replaceAll("([.*+?^${}()|\\[\\]\\\\])", "\\\\$1")
  def safeRegex(fieldName: String, pattern: String, options: String): Bson =
    Filters.regex(fieldName, escape(pattern), options)
}
