/*
 Copyright (c) 2014 by Contributors

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package ml.dmlc.xgboost4j.scala.spark

import java.nio.file.Files

import ml.dmlc.xgboost4j.{LabeledPoint => XGBLabeledPoint}
import ml.dmlc.xgboost4j.scala.DMatrix
import ml.dmlc.xgboost4j.scala.{XGBoost => SXGBoost, _}
import org.apache.hadoop.fs.{FileSystem, Path}

import org.apache.spark.TaskContext
import org.scalatest.FunSuite

class XGBoostGeneralSuite extends FunSuite with PerTest {

  test("distributed training with the specified worker number") {
    val trainingRDD = sc.parallelize(Classification.train)
    val (booster, metrics) = XGBoost.trainDistributed(
      trainingRDD,
      List("eta" -> "1", "max_depth" -> "6",
        "objective" -> "binary:logistic", "num_round" -> 5, "num_workers" -> numWorkers,
        "custom_eval" -> null, "custom_obj" -> null, "use_external_memory" -> false,
        "missing" -> Float.NaN).toMap,
      hasGroup = false)
    assert(booster != null)
  }

  test("training with external memory cache") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "binary:logistic", "num_round" -> 5, "num_workers" -> numWorkers,
      "use_external_memory" -> true)
    val model = new XGBoostClassifier(paramMap).fit(training)
    assert(eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM) < 0.1)
  }

  test("test with quantile hist with monotone_constraints (lossguide)") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1",
      "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist", "grow_policy" -> "lossguide",
      "num_round" -> 5, "num_workers" -> numWorkers, "monotone_constraints" -> "(1, 0)")
    val model = new XGBoostClassifier(paramMap).fit(training)
    assert(eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM) < 0.1)
  }

  test("test with quantile hist with interaction_constraints (lossguide)") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1",
      "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist", "grow_policy" -> "lossguide",
      "num_round" -> 5, "num_workers" -> numWorkers, "interaction_constraints" -> "[[1,2],[2,3,4]]")
    val model = new XGBoostClassifier(paramMap).fit(training)
    assert(eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM) < 0.1)
  }

  test("test with quantile hist with monotone_constraints (depthwise)") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1",
      "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist", "grow_policy" -> "depthwise",
      "num_round" -> 5, "num_workers" -> numWorkers, "monotone_constraints" -> "(1, 0)")
    val model = new XGBoostClassifier(paramMap).fit(training)
    assert(eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM) < 0.1)
  }

  test("test with quantile hist with interaction_constraints (depthwise)") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1",
      "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist", "grow_policy" -> "depthwise",
      "num_round" -> 5, "num_workers" -> numWorkers, "interaction_constraints" -> "[[1,2],[2,3,4]]")
    val model = new XGBoostClassifier(paramMap).fit(training)
    assert(eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM) < 0.1)
  }

  test("test with quantile hist depthwise") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1",
      "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist", "grow_policy" -> "depthwise",
      "num_round" -> 5, "num_workers" -> numWorkers)
    val model = new XGBoostClassifier(paramMap).fit(training)
    assert(eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM) < 0.1)
  }

  test("test with quantile hist lossguide") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1", "gamma" -> "0.5", "max_depth" -> "0",
      "objective" -> "binary:logistic", "tree_method" -> "hist", "grow_policy" -> "lossguide",
      "max_leaves" -> "8", "num_round" -> 5,
      "num_workers" -> numWorkers)
    val model = new XGBoostClassifier(paramMap).fit(training)
    val x = eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM)
    assert(x < 0.1)
  }

  test("test with quantile hist lossguide with max bin") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1", "gamma" -> "0.5", "max_depth" -> "0",
      "objective" -> "binary:logistic", "tree_method" -> "hist",
      "grow_policy" -> "lossguide", "max_leaves" -> "8", "max_bin" -> "16",
      "eval_metric" -> "error", "num_round" -> 5, "num_workers" -> numWorkers)
    val model = new XGBoostClassifier(paramMap).fit(training)
    val x = eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM)
    assert(x < 0.1)
  }

  test("test with quantile hist depthwidth with max depth") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1", "gamma" -> "0.5", "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist",
      "grow_policy" -> "depthwise", "max_depth" -> "2",
      "eval_metric" -> "error", "num_round" -> 10, "num_workers" -> numWorkers)
    val model = new XGBoostClassifier(paramMap).fit(training)
    val x = eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM)
    assert(x < 0.1)
  }

  test("test with quantile hist depthwidth with max depth and max bin") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1", "gamma" -> "0.5", "max_depth" -> "6",
      "objective" -> "binary:logistic", "tree_method" -> "hist",
      "grow_policy" -> "depthwise", "max_depth" -> "2", "max_bin" -> "2",
      "eval_metric" -> "error", "num_round" -> 10, "num_workers" -> numWorkers)
    val model = new XGBoostClassifier(paramMap).fit(training)
    val x = eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM)
    assert(x < 0.1)
  }

  test("training with spark parallelism checks disabled") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)
    val paramMap = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "binary:logistic", "timeout_request_workers" -> 0L,
      "num_round" -> 5, "num_workers" -> numWorkers)
    val model = new XGBoostClassifier(paramMap).fit(training)
    val x = eval.eval(model._booster.predict(testDM, outPutMargin = true), testDM)
    assert(x < 0.1)
  }

  test("training with checkpoint boosters") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)

    val tmpPath = Files.createTempDirectory("model1").toAbsolutePath.toString
    val paramMap = Map("eta" -> "1", "max_depth" -> 2,
      "objective" -> "binary:logistic", "checkpoint_path" -> tmpPath,
      "checkpoint_interval" -> 2, "num_workers" -> numWorkers)

    val prevModel = new XGBoostClassifier(paramMap ++ Seq("num_round" -> 5)).fit(training)
    def error(model: Booster): Float = eval.eval(
      model.predict(testDM, outPutMargin = true), testDM)

    // Check only one model is kept after training
    val files = FileSystem.get(sc.hadoopConfiguration).listStatus(new Path(tmpPath))
    assert(files.length == 1)
    assert(files.head.getPath.getName == "8.model")
    val tmpModel = SXGBoost.loadModel(s"$tmpPath/8.model")

    // Train next model based on prev model
    val nextModel = new XGBoostClassifier(paramMap ++ Seq("num_round" -> 8)).fit(training)
    assert(error(tmpModel) > error(prevModel._booster))
    assert(error(prevModel._booster) > error(nextModel._booster))
    assert(error(nextModel._booster) < 0.1)
  }

  test("training with checkpoint boosters with cached training dataset") {
    val eval = new EvalError()
    val training = buildDataFrame(Classification.train)
    val testDM = new DMatrix(Classification.test.iterator)

    val tmpPath = Files.createTempDirectory("model1").toAbsolutePath.toString
    val paramMap = Map("eta" -> "1", "max_depth" -> 2,
      "objective" -> "binary:logistic", "checkpoint_path" -> tmpPath,
      "checkpoint_interval" -> 2, "num_workers" -> numWorkers, "cacheTrainingSet" -> true)

    val prevModel = new XGBoostClassifier(paramMap ++ Seq("num_round" -> 5)).fit(training)
    def error(model: Booster): Float = eval.eval(
      model.predict(testDM, outPutMargin = true), testDM)

    // Check only one model is kept after training
    val files = FileSystem.get(sc.hadoopConfiguration).listStatus(new Path(tmpPath))
    assert(files.length == 1)
    assert(files.head.getPath.getName == "8.model")
    val tmpModel = SXGBoost.loadModel(s"$tmpPath/8.model")

    // Train next model based on prev model
    val nextModel = new XGBoostClassifier(paramMap ++ Seq("num_round" -> 8)).fit(training)
    assert(error(tmpModel) > error(prevModel._booster))
    assert(error(prevModel._booster) > error(nextModel._booster))
    assert(error(nextModel._booster) < 0.1)
  }

  test("repartitionForTrainingGroup with group data") {
    // test different splits to cover the corner cases.
    for (split <- 1 to 20) {
      val trainingRDD = sc.parallelize(Ranking.train, split)
      val traingGroupsRDD = XGBoost.repartitionForTrainingGroup(trainingRDD, 4)
      val trainingGroups: Array[Array[XGBLabeledPoint]] = traingGroupsRDD.collect()
      // check the the order of the groups with group id.
      // Ranking.train has 20 groups
      assert(trainingGroups.length == 20)

      // compare all points
      val allPoints = trainingGroups.sortBy(_(0).group).flatten
      assert(allPoints.length == Ranking.train.size)
      for (i <- 0 to Ranking.train.size - 1) {
        assert(allPoints(i).group == Ranking.train(i).group)
        assert(allPoints(i).label == Ranking.train(i).label)
        assert(allPoints(i).values.sameElements(Ranking.train(i).values))
      }
    }
  }

  test("repartitionForTrainingGroup with group data which has empty partition") {
    val trainingRDD = sc.parallelize(Ranking.train, 5).mapPartitions(it => {
      // make one partition empty for testing
      it.filter(_ => TaskContext.getPartitionId() != 3)
    })
    XGBoost.repartitionForTrainingGroup(trainingRDD, 4)
  }

  test("distributed training with group data") {
    val trainingRDD = sc.parallelize(Ranking.train, 5)
    val (booster, _) = XGBoost.trainDistributed(
      trainingRDD,
      List("eta" -> "1", "max_depth" -> "6",
        "objective" -> "rank:pairwise", "num_round" -> 5, "num_workers" -> numWorkers,
        "custom_eval" -> null, "custom_obj" -> null, "use_external_memory" -> false,
        "missing" -> Float.NaN).toMap,
      hasGroup = true)

    assert(booster != null)
  }

  test("training summary") {
    val paramMap = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "binary:logistic", "num_round" -> 5, "nWorkers" -> numWorkers)

    val trainingDF = buildDataFrame(Classification.train)
    val xgb = new XGBoostClassifier(paramMap)
    val model = xgb.fit(trainingDF)

    assert(model.summary.trainObjectiveHistory.length === 5)
    assert(model.summary.validationObjectiveHistory.isEmpty)
  }

  test("train/test split") {
    val paramMap = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "binary:logistic", "train_test_ratio" -> "0.5",
      "num_round" -> 5, "num_workers" -> numWorkers)
    val training = buildDataFrame(Classification.train)

    val xgb = new XGBoostClassifier(paramMap)
    val model = xgb.fit(training)
    assert(model.summary.validationObjectiveHistory.length === 1)
    assert(model.summary.validationObjectiveHistory(0)._1 === "test")
    assert(model.summary.validationObjectiveHistory(0)._2.length === 5)
    assert(model.summary.trainObjectiveHistory !== model.summary.validationObjectiveHistory(0))
  }

  test("train with multiple validation datasets (non-ranking)") {
    val training = buildDataFrame(Classification.train)
    val Array(train, eval1, eval2) = training.randomSplit(Array(0.6, 0.2, 0.2))
    val paramMap1 = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "binary:logistic",
      "num_round" -> 5, "num_workers" -> numWorkers)

    val xgb1 = new XGBoostClassifier(paramMap1).setEvalSets(Map("eval1" -> eval1, "eval2" -> eval2))
    val model1 = xgb1.fit(train)
    assert(model1.summary.validationObjectiveHistory.length === 2)
    assert(model1.summary.validationObjectiveHistory.map(_._1).toSet === Set("eval1", "eval2"))
    assert(model1.summary.validationObjectiveHistory(0)._2.length === 5)
    assert(model1.summary.validationObjectiveHistory(1)._2.length === 5)
    assert(model1.summary.trainObjectiveHistory !== model1.summary.validationObjectiveHistory(0))
    assert(model1.summary.trainObjectiveHistory !== model1.summary.validationObjectiveHistory(1))

    val paramMap2 = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "binary:logistic",
      "num_round" -> 5, "num_workers" -> numWorkers,
      "eval_sets" -> Map("eval1" -> eval1, "eval2" -> eval2))
    val xgb2 = new XGBoostClassifier(paramMap2)
    val model2 = xgb2.fit(train)
    assert(model2.summary.validationObjectiveHistory.length === 2)
    assert(model2.summary.validationObjectiveHistory.map(_._1).toSet === Set("eval1", "eval2"))
    assert(model2.summary.validationObjectiveHistory(0)._2.length === 5)
    assert(model2.summary.validationObjectiveHistory(1)._2.length === 5)
    assert(model2.summary.trainObjectiveHistory !== model2.summary.validationObjectiveHistory(0))
    assert(model2.summary.trainObjectiveHistory !== model2.summary.validationObjectiveHistory(1))
  }

  test("train with multiple validation datasets (ranking)") {
    val training = buildDataFrameWithGroup(Ranking.train, 5)
    val Array(train, eval1, eval2) = training.randomSplit(Array(0.6, 0.2, 0.2), 0)
    val paramMap1 = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "rank:pairwise",
      "num_round" -> 5, "num_workers" -> numWorkers, "group_col" -> "group")
    val xgb1 = new XGBoostRegressor(paramMap1).setEvalSets(Map("eval1" -> eval1, "eval2" -> eval2))
    val model1 = xgb1.fit(train)
    assert(model1 != null)
    assert(model1.summary.validationObjectiveHistory.length === 2)
    assert(model1.summary.validationObjectiveHistory.map(_._1).toSet === Set("eval1", "eval2"))
    assert(model1.summary.validationObjectiveHistory(0)._2.length === 5)
    assert(model1.summary.validationObjectiveHistory(1)._2.length === 5)
    assert(model1.summary.trainObjectiveHistory !== model1.summary.validationObjectiveHistory(0))
    assert(model1.summary.trainObjectiveHistory !== model1.summary.validationObjectiveHistory(1))

    val paramMap2 = Map("eta" -> "1", "max_depth" -> "6",
      "objective" -> "rank:pairwise",
      "num_round" -> 5, "num_workers" -> numWorkers, "group_col" -> "group",
      "eval_sets" -> Map("eval1" -> eval1, "eval2" -> eval2))
    val xgb2 = new XGBoostRegressor(paramMap2)
    val model2 = xgb2.fit(train)
    assert(model2 != null)
    assert(model2.summary.validationObjectiveHistory.length === 2)
    assert(model2.summary.validationObjectiveHistory.map(_._1).toSet === Set("eval1", "eval2"))
    assert(model2.summary.validationObjectiveHistory(0)._2.length === 5)
    assert(model2.summary.validationObjectiveHistory(1)._2.length === 5)
    assert(model2.summary.trainObjectiveHistory !== model2.summary.validationObjectiveHistory(0))
    assert(model2.summary.trainObjectiveHistory !== model2.summary.validationObjectiveHistory(1))
  }

  test("infer with different batch sizes") {
    val regModel = new XGBoostRegressor(Map(
      "eta" -> "1",
      "max_depth" -> "6",
      "silent" -> "1",
      "objective" -> "reg:squarederror",
      "num_round" -> 5,
      "num_workers" -> numWorkers))
        .fit(buildDataFrame(Regression.train))
    val regDF = buildDataFrame(Regression.test)

    val regRet1 = regModel.transform(regDF).collect()
    val regRet2 = regModel.setInferBatchSize(1).transform(regDF).collect()
    val regRet3 = regModel.setInferBatchSize(10).transform(regDF).collect()
    val regRet4 = regModel.setInferBatchSize(32 << 15).transform(regDF).collect()
    assert(regRet1 sameElements regRet2)
    assert(regRet1 sameElements regRet3)
    assert(regRet1 sameElements regRet4)

    val clsModel = new XGBoostClassifier(Map(
      "eta" -> "1",
      "max_depth" -> "6",
      "silent" -> "1",
      "objective" -> "binary:logistic",
      "num_round" -> 5,
      "num_workers" -> numWorkers))
        .fit(buildDataFrame(Classification.train))
    val clsDF = buildDataFrame(Classification.test)

    val clsRet1 = clsModel.transform(clsDF).collect()
    val clsRet2 = clsModel.setInferBatchSize(1).transform(clsDF).collect()
    val clsRet3 = clsModel.setInferBatchSize(10).transform(clsDF).collect()
    val clsRet4 = clsModel.setInferBatchSize(32 << 15).transform(clsDF).collect()
    assert(clsRet1 sameElements clsRet2)
    assert(clsRet1 sameElements clsRet3)
    assert(clsRet1 sameElements clsRet4)
  }
}
