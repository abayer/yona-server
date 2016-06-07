/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.Duration
import java.time.ZonedDateTime

import nu.yona.server.test.AppActivity

class AppActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to post app activity without password'()
	{
		given:
		def richard = addRichard()
		ZonedDateTime now = ZonedDateTime.now()
		ZonedDateTime startTime = now.minus(Duration.ofHours(1))
		ZonedDateTime endTime = now
		def nowString = YonaServer.toIsoDateString(now)
		def startTimeString = YonaServer.toIsoDateString(startTime)
		def endTimeString = YonaServer.toIsoDateString(endTime)

		when:
		def response = appService.createResourceWithPassword(richard.appActivityUrl, """{
				"deviceDateTime" : "$nowString",
				"activities" : [{
					"application":"Poker App",
					"startTime":"$startTimeString",
					"endTime":"$endTimeString"
				}]}""", "Hack")

		then:
		response.status == 400
		response.responseData.code == "error.decrypting.data"
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		ZonedDateTime now = ZonedDateTime.now()
		ZonedDateTime startTime = now.minus(Duration.ofHours(1))
		ZonedDateTime endTime = now

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Poker App", startTime, endTime))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		goalConflictMessagesRichard[0].nickname == "<self>"
		assertEquals(goalConflictMessagesRichard[0].creationTime, ZonedDateTime.now())
		goalConflictMessagesRichard[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		goalConflictMessagesBob[0].nickname == richard.nickname
		assertEquals(goalConflictMessagesBob[0].creationTime, ZonedDateTime.now())
		assertEquals(goalConflictMessagesBob[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesBob[0].activityEndTime, endTime)
		goalConflictMessagesBob[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Device time difference is properly resolved'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		Duration offset = Duration.ofMinutes(45) // Off by 45 minutes
		ZonedDateTime now = ZonedDateTime.now()
		ZonedDateTime startTime = now.minus(Duration.ofHours(1))
		ZonedDateTime endTime = now

		when:
		def nowWrong = now.plus(offset)
		def startTimeWrong = startTime.plus(offset)
		def endTimeWrong = endTime.plus(offset)
		def response = appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity(nowWrong, "Poker App", startTimeWrong, endTimeWrong))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		goalConflictMessagesRichard[0].nickname == "<self>"
		assertEquals(goalConflictMessagesRichard[0].creationTime, now)
		goalConflictMessagesRichard[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		goalConflictMessagesBob[0].nickname == richard.nickname
		assertEquals(goalConflictMessagesBob[0].creationTime, now)
		assertEquals(goalConflictMessagesBob[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesBob[0].activityEndTime, endTime)
		goalConflictMessagesBob[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Two conflicts within the conflict interval are reported as one message for each person'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		ZonedDateTime now = ZonedDateTime.now()
		ZonedDateTime startTime = now.minus(Duration.ofHours(1))
		ZonedDateTime endTime = now
		ZonedDateTime startTime1 = now.minus(Duration.ofSeconds(10))
		ZonedDateTime endTime1 = now

		when:
		appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Poker App", startTime, endTime))
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Lotto App", startTime1, endTime1))

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
	}

	def 'Send multiple app activities after offline period'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		ZonedDateTime now = ZonedDateTime.now()
		ZonedDateTime startTime = now.minus(Duration.ofHours(1))
		ZonedDateTime endTime = now.minus(Duration.ofSeconds(10))
		ZonedDateTime startTime1 = endTime
		ZonedDateTime endTime1 = ZonedDateTime.now()

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard,
				new AppActivity([new AppActivity.Activity("Poker App", startTime, endTime), new AppActivity.Activity("Lotto App", , startTime1, endTime1)].toArray()))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		assertEquals(goalConflictMessagesRichard[0].creationTime, now)
		assertEquals(goalConflictMessagesRichard[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesRichard[0].activityEndTime, endTime1)

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		assertEquals(goalConflictMessagesRichard[0].creationTime, now)
		assertEquals(goalConflictMessagesRichard[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesRichard[0].activityEndTime, endTime1)
	}
}
