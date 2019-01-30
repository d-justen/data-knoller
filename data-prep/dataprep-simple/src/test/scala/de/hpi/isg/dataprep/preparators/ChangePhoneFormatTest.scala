package de.hpi.isg.dataprep.preparators

import de.hpi.isg.dataprep.DialectBuilder
import de.hpi.isg.dataprep.components.Preparation
import de.hpi.isg.dataprep.exceptions.ParameterNotSpecifiedException
import de.hpi.isg.dataprep.load.FlatFileDataLoader
import de.hpi.isg.dataprep.metadata.{PhoneNumberFormat, PhoneNumberFormatComponentType}
import de.hpi.isg.dataprep.model.target.errorlog.ErrorLog
import de.hpi.isg.dataprep.preparators.define.ChangePhoneFormat
import org.apache.log4j.{Level, Logger}

import scala.collection.JavaConverters._

class ChangePhoneFormatTest extends PreparatorScalaTest {
	import PhoneNumberFormatComponentType._

	"ChangePhoneFormat" should "verify the pre execution conditions" in {
		val preparator = new ChangePhoneFormat("phone", null, null)
		val preparation = new Preparation(preparator)
		pipeline.addPreparation(preparation)
		an[ParameterNotSpecifiedException] should be thrownBy pipeline.executePipeline()
	}

	it should "calculate the applicability score" in {
		val sourceFormat = PhoneNumberFormat(List(AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val targetFormat = PhoneNumberFormat(List(AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val preparator = new ChangePhoneFormat("phone", sourceFormat, targetFormat)

		//preparator.calApplicability()
	}

	"ChangePhoneFormatImpl" should "convert a phone number from a given source format to a target format" in {
		val sourceFormat = PhoneNumberFormat(List(AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val targetFormat = PhoneNumberFormat(List(AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val preparator = new ChangePhoneFormat("phone", sourceFormat, targetFormat)
		val preparation = new Preparation(preparator)

		pipeline.addPreparation(preparation)
		pipeline.executePipeline()
		pipeline.getRawData.show()

		val expectedErrors = Set.empty[ErrorLog]
		val realErrors = pipeline.getErrorRepository.getErrorLogs.asScala.toSet

		expectedErrors shouldEqual realErrors
	}

	it should "handle default values" in {
		val sourceFormat = PhoneNumberFormat(List(AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val targetFormat = PhoneNumberFormat(List(CountryCode.optional("+1"), AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val preparator = new ChangePhoneFormat("phone", sourceFormat, targetFormat)
		val preparation = new Preparation(preparator)

		pipeline.addPreparation(preparation)
		pipeline.executePipeline()
		pipeline.getRawData.show()

		val expectedErrors = Set.empty[ErrorLog]
		val realErrors = pipeline.getErrorRepository.getErrorLogs.asScala.toSet

		expectedErrors shouldEqual realErrors
	}

	it should "handle missing source format" in {
		val targetFormat = PhoneNumberFormat(List(AreaCode.required, CentralOfficeCode.required, LineNumber.required))
		val preparator = new ChangePhoneFormat("phone", targetFormat)
		val preparation = new Preparation(preparator)

		pipeline.addPreparation(preparation)
		pipeline.executePipeline()
		pipeline.getRawData.show()

		val expectedErrors = Set.empty[ErrorLog]
		val realErrors = pipeline.getErrorRepository.getErrorLogs.asScala.toSet

		expectedErrors shouldEqual realErrors
	}

	override def beforeAll: Unit = {
		Logger.getLogger("org").setLevel(Level.OFF)
		Logger.getLogger("akka").setLevel(Level.OFF)

		val filePath = getClass.getResource("/restaurants.tsv").getPath
		val dialect = new DialectBuilder()
			.hasHeader(true)
			.inferSchema(true)
			.url(filePath)
			.delimiter("\t")
			.buildDialect()

		val dataLoader = new FlatFileDataLoader(dialect)
		dataContext = dataLoader.load()

		super.beforeAll()
	}
}
