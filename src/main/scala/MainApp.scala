import org.apache.spark.ml.classification.{RandomForestClassificationModel, RandomForestClassifier}
import org.apache.spark.ml.feature.{QuantileDiscretizer, StringIndexer, VectorAssembler}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{col, concat_ws, udf}




object MainApp {

    /**************************/
   /***** INDEX METHODS ******/
  /**************************/

  /**
    * Index the string values
    * @param dataFrame data container
    * @param columnNames each column name of string value
    * @return the new dataframe with the indexed string
    */
  def indexTypeString(dataFrame: DataFrame, columnNames: String*): DataFrame = {
    // create a string indexer for each string column :
    // StringIndexer encodes a string column of labels
    // to a column of label indices. The indices are in
    // [0, numLabels), ordered by label frequencies.
    columnNames.map { name => {
      new StringIndexer()
        .setInputCol(name)
        .setOutputCol(s"${name}Index")
        .fit(dataFrame)
      // apply the transformation to the dataframe
    }}.foldLeft(dataFrame) { (dataFrame, indexer) => {
      indexer.transform(dataFrame)
    }}
  }

  /**
    * Index the boolean values by converting them in double
    * @param dataFrame data container
    * @param columnNames each column name of boolean value
    * @return the new dataframe with the indexed converted boolean
    */
  def indexTypeBool(dataFrame: DataFrame, columnNames: String*): DataFrame = {
    // create the converter from boolean to double
    val toBool = udf((bool: Boolean) => if (bool) 1.0 else 0.0)
    // loop in the different columns and create a new indexed column with the conversion
    columnNames.foldLeft(dataFrame) { (dataFrame: DataFrame, name) => {
      dataFrame.withColumn(s"${name}Index", toBool(dataFrame(name)))
    }}
  }

  /**
    * Index the double values
    * @param dataFrame data container
    * @param numBuckets the number of bins
    * @return the new dataframe with indexed double value
    */
  def indexTypeDouble(dataFrame: DataFrame, numBuckets: Int): DataFrame = {
    // QuantileDiscretizer takes a column
    // with continuous features and outputs a column
    // with binned categorical features. The number of bins
    // is set by the numBuckets parameter.
    val qd = new QuantileDiscretizer()
      .setInputCol("bidfloor")
      .setOutputCol("bidfloorIndex")
      .setNumBuckets(numBuckets)
    // add the new index values to the dataframe
    qd.fit(dataFrame).transform(dataFrame)
  }



    /***************************/
   /***** MODEL CREATION ******/
  /**************************/


  /**
    * Train a random forest model for the future prediction
    * @param dataFrame data container
    */
  def randomForestModel(dataFrame: DataFrame): Unit = {
    // train a random forest model with the dataframe
    val randomForestClassifier = new RandomForestClassifier()
      .setNumTrees(10)
      .setMaxBins(50000)
      .setLabelCol("labelIndex")
      .setFeaturesCol("features")
      .setPredictionCol("prediction")
    randomForestClassifier
      .fit(dataFrame)
      .write.overwrite.save("./model")
  }



    /**************************/
   /******** PREDICTION ******/
  /**************************/

  /**
    * Predict the ad clicks via random forest model
    * @param dataFrame data container
    * @return the prediction dataframe
    */
  def predict(dataFrame: DataFrame) : DataFrame = {
    // create the prediction of the dataframe based on the random forest model
    val predictions = Some(RandomForestClassificationModel.read.load("./model")).get.transform(dataFrame)
    predictions
  }


  /**
    * Clean the predictions output
    * @param dataFrame the prediction data container
    * @return the new cleaned prediction
    */
  def cleanResult(dataFrame: DataFrame) : DataFrame = {
    // init the converter from double to boolean
    val toBoolean = udf((double: Double) => double == 1.0)
    // return
    dataFrame
      // select only the first dataframe columns + prediction
      .select(
      "prediction","appOrSite","bidfloor","city","exchange","impid","interests","media",
      "network","os","timestamp","type","user")
      // convert the prediction values from double to boolean
      .withColumn("prediction", toBoolean(dataFrame.col("prediction")))
      // rename the prediction column to label
      .withColumnRenamed("prediction", "label")
  }



    /**************************/
   /****** CSV CREATION ******/
  /**************************/


  /**
    * Write a CSV file from the dataframe
    * @param dataFrame data container
    */
  def createCSV(dataFrame: DataFrame): Unit = {
    // specify the csv format and file path
    dataFrame
      .coalesce(1)
      .write.format("com.databricks.spark.csv")
      .option("header", "true")
      .option("delimiter", ",")
      .save("output")
  }




    /**************************/
   /************** MAIN ******/
  /**************************/

  /**
    * The MAIN class, running the algo
    * @param args
    */
  def main(args: Array[String]) {

    /* initialize a spark session for the project */
    implicit val spark: SparkSession = SparkSession
      .builder()
      .appName("WebIntelligence")
      .master("local[*]")
      .config("spark.executor.memory", "15g")
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    /* read the data and create the dataframe */
    var df: DataFrame = spark.read.json(args(0))

    /* fill the empty field with "null" value */
    df = df.na.fill("null", Seq("exchange"))
    df = df.na.fill("null", Seq("interests"))
    df = df.na.fill("null", Seq("os"))
    df = df.na.fill("null", Seq("publisher"))
    df = df.na.fill("null", Seq("type"))
    df = df.na.fill(0.0, Seq("bidfloor"))
      
    /* split the IAB part of interests to have clean data for the model */
    val adjustInterests = udf((col: String) => {
        val values = col.split(",")
        var result = "null"
       if (values.nonEmpty) {
         var i = 0
         while (i < values.length && result == "null") {
           if (!values(i).startsWith("IAB")) result = values(i)
             i = i + 1
         }
         result
       }
       else "null"
      })
     df = df.withColumn("interests", adjustInterests(df("interests")))




    //-------------INDEX--------------
    df = indexTypeString(df, "type", "exchange", "os", "interests")
    df = indexTypeBool(df, "label")
    df = indexTypeDouble(df, 2)


    //-------------CONVERT------------
    // add features as a base for the classification
    // we focus on the type, exhange, OS and the interests
    df = new VectorAssembler()
      .setInputCols(Array("typeIndex", "exchangeIndex", "osIndex", "interestsIndex"))
      .setOutputCol("features")
      .transform(df)


    //--------CREATE THE MODEL--------
    randomForestModel(df)


    //----------PREDICT+CLEANING------
    df = predict(df)
    df = cleanResult(df)

    //----------METRICS---------------
    val dfL = df.select("label").collect().map(_(0)).toList
    val df2L = df2.select("label").collect().map(_(0)).toList
    var i=0
    // loop in the real labels and the predicted labels
    do {
      println(dfL(i))
      println(df2L(i))
      // increment the correct confusion matrix value
      if (dfL(i).toString == "true" && df2L(i).toString=="true") {
        println("trueT")
        trueT = trueT +1
      }
      else if (dfL(i).toString == "false" && df2L(i).toString=="false") {
        println("trueF")
        trueF = trueF +1
      }
      else if (dfL(i).toString=="false" && df2L(i).toString=="true") {
        println("falseF")
        falseF = falseF +1
      }
      else if (dfL(i).toString=="true" && df2L(i).toString=="false"){
        println("falseT")
        falseT = falseT +1
      }
      println(trueT)
      println(trueF)
      println(falseT)
      println(falseF)

      i=i+1
    }
    while (i!=1000000)
    // FINAL CONFUSION MATRIX
    println(trueT)
    println(trueF)
    println(falseT)
    println(falseF)

    //----------CSV-------------------
    createCSV(df)



  }
}
