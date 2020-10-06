/*
 * Copyright (C) 2020 Edouard Fouché
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import com.typesafe.scalalogging.LazyLogging
import io.github.edouardfouche.experiments._
import io.github.edouardfouche.utils.StopWatch

object Main extends LazyLogging {
  def main(args: Array[String]): Unit = {
    val unit = "ms"

    info("Working directory: " + System.getProperty("user.dir"))
    info("Raw parameters given: " + args.map(s => "\"" + s + "\"").mkString("[", ", ", "]"))

    require(args.length > 0, "No arguments given. Please see README.md")

    StopWatch.start
    val result = startJob(experimentFactory(args(0)))
    val (cpu, wall) = StopWatch.stop(unit)

    println(s"Computation time: \t ${result._1} $unit (cpu), ${result._2} $unit (wall)")
    println(s"Total elapsed time: \t $cpu $unit (cpu), $wall $unit (wall)")
    System.exit(0)
  }

  def info(s: String): Unit = logger.info(s)

  def startJob[R](block: => R, unit: String = "ms"): (Double, Double, R) = {
    val res = StopWatch.measureTime(block, unit)
    res._3 match {
      case a:Double => println(a)
      case a:Array[Array[Double]] => print_matrix(a)
      case _ => println("Unknown type")
    }
    def print_matrix(a: Array[Array[Double]]): Unit = {
      val matrix = a.map{x =>
        if(x.length > 10) (x.take(10).map(y => f"$y%1.2f") mkString "\t") ++ "\t ... (truncated)"
        else x.map(y => f"$y%1.2f") mkString "\t"
      }
      val toprint = if(matrix.length > 10)
        (matrix.take(10) ++ Array((1 to 10).map(x => "...") mkString "\t")) ++ Array("(truncated)")
      else matrix
      toprint.foreach{x => println(x)}
    }
    res
  }

  def experimentFactory(arg: String): Unit = arg match { // Hey
    case "com.edouardfouche.experiments.Entropy_KJNN" => Entropy_KJNN.run()
    case "com.edouardfouche.experiments.Evaluate_KJNN" => Evaluate_KJNN.run()
    case "com.edouardfouche.experiments.Ablation" => Ablation.run()
    case "com.edouardfouche.experiments.Exploit_KJNN" => Exploit_KJNN.run()
    case "com.edouardfouche.experiments.Evaluate_LOF" => Evaluate_LOF.run()
    case "com.edouardfouche.experiments.Evaluate_AvgNegCosSim" => Evaluate_AvgNegCosSim.run()
    case "com.edouardfouche.experiments.Evaluate_Hash" => Evaluate_Hash.run() // this is a version of RS-Hash, but on the full space
    case "com.edouardfouche.experiments.Evaluate_RSHash" => Evaluate_RSHash.run()

    case "com.edouardfouche.experiments.MiningTextOutliers" => {
      Entropy_KJNN.run()
      Ablation.run()
      Evaluate_KJNN.run()
      Evaluate_RSHash.run()
      Evaluate_LOF.run()
      Evaluate_AvgNegCosSim.run()
      Exploit_KJNN.run()
    }

    case _ => throw new Error(s"Unknown experiment $arg")
  }

  def warn(s: String): Unit = logger.warn(s)
}
