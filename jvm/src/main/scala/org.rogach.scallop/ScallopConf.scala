package org.rogach.scallop

abstract class ScallopConf(
  args: Seq[String] = Nil,
  commandNameAndAliases: Seq[String] = Nil
) extends ScallopConfBase(args, commandNameAndAliases) {

  // machinery to support option name guessing
  override protected def guessOptionNameDefault: Boolean = true
  /** If true, scallop would try to guess missing option names from the names of their fields. */
  def guessOptionName = _guessOptionName
  /** If set to true, scallop would try to guess missing option names from the names of their fields. */
  def guessOptionName_=(v: Boolean) { _guessOptionName = v }

  override protected def performOptionNameGuessing() {
    val methodsAndOptions =
      this.getClass.getMethods
      .filterNot(classOf[ScallopConf].getMethods.toSet)
      .filterNot(_.getName.endsWith("$eq"))
      .filterNot(_.getName.endsWith("$outer"))
      .filter(_.getReturnType == classOf[ScallopOption[_]])
      .filter(_.getParameterTypes.isEmpty)
      .map { m =>
        val opt = m.invoke(this).asInstanceOf[ScallopOption[_]]
        (m, opt)
      }
      .filter(_._2.name.contains("\t"))

    val nameMap = methodsAndOptions.sortBy(-_._2._transformCount).map { case (m, opt) =>
      val newName = m.getName.flatMap(c => if (c.isUpper) Seq('-', c.toLower) else Seq(c))
      // the old, generated version of name, without prefixes from parent builders
      val shortGenName = '\t' +: opt.name.reverse.takeWhile('\t'!=).reverse
      (opt.name, (shortGenName, newName))
    }.toMap

    methodsAndOptions.foreach { case (m, opt) =>
      val (shortGenName, newName) = nameMap(opt.name)
      editBuilder(e => e.copy(opts = e.opts.map { o =>
        if (o.name == shortGenName) {
          o match {
            case o: SimpleOption => o.copy(name = newName)
            case o: TrailingArgsOption => o.copy(name = newName)
            case o: ToggleOption => o.copy(name = newName)
            case o: NumberArgOption => o.copy(name = newName)
            case _ => o
          }
        } else o
      }))
      opt._name = newName
    }
  }

  errorMessageHandler = { message =>
    if (overrideColorOutput.value.getOrElse(System.console() != null)) {
      Console.err.println("[\u001b[31m%s\u001b[0m] Error: %s" format (printedName, message))
    } else {
      // no colors on output
      Console.err.println("[%s] Error: %s" format (printedName, message))
    }
    sys.exit(1)
  }

}
