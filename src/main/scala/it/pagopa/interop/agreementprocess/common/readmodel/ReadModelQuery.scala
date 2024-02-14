package it.pagopa.interop.agreementprocess.common.readmodel

trait ReadModelQuery {
  def mapToVarArgs[A, B](l: Seq[A])(f: Seq[A] => B): Option[B] = Option.when(l.nonEmpty)(f(l))

  def escape(str: String): String = str.replaceAll("([.*+?^${}()|\\[\\]\\\\])", "\\\\$1")
}
