package nu.yona.server.exceptions;

import java.util.UUID;

/**
 * This exception is to be used in case data is wrong in DTOs. So whenever a field has a wrong value you should throw this
 * exception.
 * 
 * @author pgussow
 */
public class InvalidDataException extends YonaException
{
	private InvalidDataException(String messageId, Object... parameters)
	{
		super(messageId, parameters);
	}

	private InvalidDataException(Throwable t, String messageId, Object... parameters)
	{
		super(t, messageId, parameters);
	}

	public static InvalidDataException analysisUserNotFound(UUID id)
	{
		return new InvalidDataException("error.analysis.user.not.found", id);
	}

	public static InvalidDataException blankFirstName()
	{
		return new InvalidDataException("error.user.firstname");
	}

	public static InvalidDataException blankLastName()
	{
		return new InvalidDataException("error.user.lastname");
	}

	public static InvalidDataException blankMobileNumber()
	{
		return new InvalidDataException("error.user.mobile.number");
	}

	public static InvalidDataException invalidMobileNumber(String mobileNumber)
	{
		return new InvalidDataException("error.user.mobile.number.invalid");
	}
}
