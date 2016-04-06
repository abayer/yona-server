/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.exceptions;

/**
 * This exception is to be used in case the mobile number confirmation code is wrong.
 */
public class MobileNumberConfirmationException extends YonaException
{
	private static final long serialVersionUID = -7917208280838423613L;
	public static final String FAILED_ATTEMPT_MESSAGE_ID = "error.mobile.number.confirmation.code.mismatch";
	private final int remainingAttempts;

	private MobileNumberConfirmationException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
		this.remainingAttempts = -1;
	}

	private MobileNumberConfirmationException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
		this.remainingAttempts = -1;
	}

	public MobileNumberConfirmationException(int remainingAttempts, String messageId, Object... parameters)
	{
		super(messageId, parameters);
		this.remainingAttempts = remainingAttempts;
	}

	public static MobileNumberConfirmationException confirmationCodeMismatch(String mobileNumber, String code,
			int remainingAttempts)
	{
		return new MobileNumberConfirmationException(remainingAttempts, FAILED_ATTEMPT_MESSAGE_ID, mobileNumber, code);
	}

	public static MobileNumberConfirmationException confirmationCodeNotSet(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.confirmation.code.not.set", mobileNumber);
	}

	public static MobileNumberConfirmationException mobileNumberAlreadyConfirmed(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.already.confirmed", mobileNumber);
	}

	public static MobileNumberConfirmationException notConfirmed(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.not.confirmed", mobileNumber);
	}

	public static MobileNumberConfirmationException tooManyAttempts(String mobileNumber)
	{
		return new MobileNumberConfirmationException("error.mobile.number.confirmation.code.too.many.failed.attempts",
				mobileNumber);
	}

	public int getRemainingAttempts()
	{
		return remainingAttempts;
	}
}
