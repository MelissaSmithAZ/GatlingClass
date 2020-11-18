package simulations

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt
import scala.util.Random

class FinalPractice extends Simulation {

  private def getProperty(propertyName: String, defaultValue: String) = {
    Option(System.getenv(propertyName))
      .orElse(Option(System.getProperty(propertyName)))
      .getOrElse(defaultValue)
  }

  def userCount: Int = getProperty("USERS", "5").toInt
  def rampDuration: Int = getProperty("RAMP_DURATION", "10").toInt
  def testDuration: Int = getProperty("DURATION", "60").toInt

  before {
    println(s"Running test with ${userCount} users")
    println(s"Ramping users over ${rampDuration} seconds")
    println(s"Total test duration: ${testDuration} seconds")
  }
  val httpConf = http.baseUrl("http://localhost:8080/app/")
    .header("Accept", "application/json")

  var idNumbers = (11 to 99).iterator
//  var idNumbers = (11).iterator
  val rnd = new Random()
  val now = LocalDate.now()
  val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val csvFeeder = csv("data/gameCsvFile.csv").circular

  def randomString(length: Int) = {
    rnd.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  def getRandomDate(startDate: LocalDate, random: Random): String = {
    startDate.minusDays(random.nextInt(30)).format(pattern)
  }



  def getSpecificVideoGame() = {
    repeat(2) {
      feed(csvFeeder)
        .exec(http("Get specific video game")
          .get("videogames/${gameId}")
          .check(jsonPath("$.name").is("${gameName}"))
          .check(status.is(200)))
        .pause(1)
    }
  }

  def deleteSpecificGame() = {
        exec(http("Delete video game")
          .delete("videogames/${gameId}")
          .check(status.is(200)))
  }
  val customFeeder = Iterator.continually(Map(
    "gameId" -> idNumbers.next(),
    "name" -> ("Game-" + randomString(5)),
    "releaseDate" -> getRandomDate(now, rnd),
    "reviewScore" -> rnd.nextInt(100),
    "category" -> ("Category-" + randomString(6)),
    "rating" -> ("Rating-" + randomString(4))
  ))

  def getAllVideoGames() = {
    exec(
      http("Get all  video games")
        .get("videogames")
        .check(status.is(200))
    )
  }


  def postNewGame() = {
    repeat(1) {
      feed(customFeeder)
        .exec(http("Post New Game")
          .post("videogames/")
          .body(ElFileBody("bodies/NewGameTemplate.json")).asJson
          .check(status.is(200)))
        .pause(1)
    }
  }


  val scn = scenario("Post, delete , get games")
      .exec(postNewGame())
      .exec(deleteSpecificGame())
      .exec(getAllVideoGames())
      .exec(getSpecificVideoGame())



  setUp(
    scn.inject(
      nothingFor(5 seconds) ,
      atOnceUsers(1),
      rampUsers(userCount) during (rampDuration seconds))
  ).protocols(httpConf)
    .maxDuration(testDuration seconds)
  after{
    println("Stress Test Complte")
  }
}
