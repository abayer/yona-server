/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.ZonedDateTime

import nu.yona.server.test.AppActivity
import nu.yona.server.test.Goal
import nu.yona.server.test.User

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Page through multiple weeks'()
	{
		given:
		def richard = addRichard()

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-4 Mon 02:18")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		def budgetGoalNewsUrl = richard.findActiveGoal(NEWS_ACT_CAT_URL).url

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + 7*3 + currentDayOfWeek + 1
		def expectedTotalWeeks = 5

		when:
		//we can safely get two normal pages
		def responseWeekOverviewsPage1 = appService.getWeekActivityOverviews(richard)
		def responseWeekOverviewsPage2 = appService.getWeekActivityOverviews(richard, ["page": 1])
		def responseWeekOverviewsPage3 = appService.getWeekActivityOverviews(richard, ["page": 2])
		//we can safely get two normal pages
		def responseDayOverviewsPage1 = appService.getDayActivityOverviews(richard)
		def responseDayOverviewsPage2 = appService.getDayActivityOverviews(richard, ["page": 1])

		then:
		assertWeekOverviewBasics(responseWeekOverviewsPage1, [2, 1], expectedTotalWeeks)
		assertWeekOverviewBasics(responseWeekOverviewsPage2, [1, 1], expectedTotalWeeks)
		assertWeekOverviewBasics(responseWeekOverviewsPage3, [1], expectedTotalWeeks)

		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve up test report of previous week'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Mon 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		def budgetGoalNewsUrl = richard.findActiveGoal(NEWS_ACT_CAT_URL).url
		def timeZoneGoalSocialUrl = richard.findActiveGoal(SOCIAL_ACT_CAT_URL).url

		def expectedValuesLastWeek = [
			"Mon" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + currentDayOfWeek + 1
		def expectedTotalWeeks = 2

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 14])
		//the min amount of days is 1 this week + 6 previous week, so we can safely get two normal pages
		def responseDayOverviewsPage1 = appService.getDayActivityOverviews(richard)
		def responseDayOverviewsPage2 = appService.getDayActivityOverviews(richard, ["page": 1])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 2], expectedTotalWeeks)
		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsUrl, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialUrl, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Sat")

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Add activity after retrieving the report'()
	{
		given:
		User richard = addRichard()
		def ZonedDateTime now = YonaServer.now
		def budgetGoalGamblingUrl = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).url
		def budgetGoalNewsUrl = richard.findActiveGoal(NEWS_ACT_CAT_URL).url
		def currentShortDay = getCurrentShortDay(now)
		def initialExpectedValues = [
			(currentShortDay) : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:budgetGoalGamblingUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def initialResponseWeekOverviews = appService.getWeekActivityOverviews(richard)
		def initialResponseDayOverviews = appService.getDayActivityOverviews(richard)
		assert initialResponseWeekOverviews.status == 200
		def initialCurrentWeekOverview = initialResponseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0]
		assertDayInWeekOverviewForGoal(initialCurrentWeekOverview, budgetGoalNewsUrl, initialExpectedValues, currentShortDay)
		assertWeekDetailForGoal(richard, initialCurrentWeekOverview, budgetGoalNewsUrl, initialExpectedValues)
		assertDayOverviewForBudgetGoal(initialResponseDayOverviews, budgetGoalNewsUrl, initialExpectedValues, 0, currentShortDay)
		assertDayDetail(richard, initialResponseDayOverviews, budgetGoalNewsUrl, initialExpectedValues, 0, currentShortDay)

		when:
		reportAppActivities(richard, AppActivity.singleActivity("NU.nl", now, now))

		then:
		def expectedValuesAfterActivity = [
			(currentShortDay) : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [(getCurrentSpreadCell(now)) : 1]]], [goalUrl:budgetGoalGamblingUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def responseWeekOverviewsAfterActivity = appService.getWeekActivityOverviews(richard)
		def currentWeekOverviewAfterActivity = responseWeekOverviewsAfterActivity.responseData._embedded."yona:weekActivityOverviews"[0]
		assertDayInWeekOverviewForGoal(currentWeekOverviewAfterActivity, budgetGoalNewsUrl, expectedValuesAfterActivity, currentShortDay)
		assertWeekDetailForGoal(richard, currentWeekOverviewAfterActivity, budgetGoalNewsUrl, expectedValuesAfterActivity)

		def responseDayOverviewsAfterActivity = appService.getDayActivityOverviews(richard)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAfterActivity, budgetGoalNewsUrl, expectedValuesAfterActivity, 0, currentShortDay)
		assertDayDetail(richard, responseDayOverviewsAfterActivity, budgetGoalNewsUrl, expectedValuesAfterActivity, 0, currentShortDay)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Remove goal after adding activities'()
	{
		given:
		User richard = addRichard()
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Mon 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		def budgetGoalNewsUrl = budgetGoalNews.url
		Goal timeZoneGoalSocial = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)
		def timeZoneGoalSocialUrl = timeZoneGoalSocial.url

		def expectedValuesLastWeekBeforeDelete = [
			"Mon" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def expectedGoalsPerWeekBeforeDelete = [3, 2]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDaysBeforeDelete = 6 + currentDayOfWeek + 1
		def expectedTotalWeeksBeforeDelete = 2
		def responseWeekOverviewsBeforeDelete = appService.getWeekActivityOverviews(richard)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAllBeforeDelete = appService.getDayActivityOverviews(richard, ["size": 14])

		assertWeekOverviewBasics(responseWeekOverviewsBeforeDelete, expectedGoalsPerWeekBeforeDelete, expectedTotalWeeksBeforeDelete)
		def weekOverviewLastWeekBeforeDelete = responseWeekOverviewsBeforeDelete.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekBeforeDelete, budgetGoalNewsUrl, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, "Wed")

		assertWeekDetailForGoal(richard, weekOverviewLastWeekBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete)

		assertDayOverviewBasics(responseDayOverviewsAllBeforeDelete, expectedTotalDaysBeforeDelete, expectedTotalDaysBeforeDelete, 14)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, 1, "Wed")

		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNewsUrl, expectedValuesLastWeekBeforeDelete, 1, "Wed")

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocialUrl, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekBeforeDelete, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekBeforeDelete, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekBeforeDelete, "Fri")

		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekBeforeDelete, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekBeforeDelete, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekBeforeDelete, 1, "Fri")

		when:
		def response = appService.removeGoal(richard, budgetGoalNews)

		then:
		def expectedTotalDaysAfterDelete = expectedTotalDaysBeforeDelete - 2
		def expectedTotalWeeksAfterDelete = expectedTotalWeeksBeforeDelete
		def expectedValuesLastWeekAfterDelete = [
			"Mon" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def expectedGoalsPerWeekAfterDelete = expectedGoalsPerWeekBeforeDelete.collect{it - 1}

		//get all days at once (max 2 weeks) to make assertion easy
		def responseWeekOverviewsAfterDelete = appService.getWeekActivityOverviews(richard)
		assertWeekOverviewBasics(responseWeekOverviewsAfterDelete, expectedGoalsPerWeekAfterDelete, expectedTotalWeeksAfterDelete)

		def weekOverviewLastWeekAfterDelete = responseWeekOverviewsAfterDelete.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekAfterDelete, timeZoneGoalSocialUrl, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekAfterDelete, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekAfterDelete, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekAfterDelete, "Fri")

		def responseDayOverviewsAllAfterDelete = appService.getDayActivityOverviews(richard, ["size": 14])
		assertDayOverviewBasics(responseDayOverviewsAllAfterDelete, expectedTotalDaysAfterDelete, expectedTotalDaysAfterDelete, 14)
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekAfterDelete, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekAfterDelete, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocialUrl, expectedValuesLastWeekAfterDelete, 1, "Fri")

		// See whether we can still report activities the activity category of the deleted goal
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")

		// Activity report should be identical
		def responseWeekOverviewsNewActivity = appService.getWeekActivityOverviews(richard)
		assertWeekOverviewBasics(responseWeekOverviewsNewActivity, expectedGoalsPerWeekAfterDelete, expectedTotalWeeksAfterDelete)

		def weekOverviewLastWeekAfterNewActivity = responseWeekOverviewsNewActivity.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekAfterNewActivity, timeZoneGoalSocialUrl, 4)

		def responseDayOverviewsAllAfterNewActivity = appService.getDayActivityOverviews(richard, ["size": 14])
		assertDayOverviewBasics(responseDayOverviewsAllAfterNewActivity, expectedTotalDaysAfterDelete, expectedTotalDaysAfterDelete, 14)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Add goals and fetch activities'()
	{
		given:
		User richard = addRichard()
		addTimeZoneGoal(richard, MULTIMEDIA_ACT_CAT_URL, ["11:00-12:00"])
		assert appService.getDayActivityOverviews(richard).status == 200
		assert appService.getWeekActivityOverviews(richard).status == 200

		when:
		addBudgetGoal(richard, COMMUNICATION_ACT_CAT_URL, 60)
		then:
		appService.getDayActivityOverviews(richard).status == 200
		appService.getWeekActivityOverviews(richard).status == 200

		cleanup:
		appService.deleteUser(richard)
	}
}