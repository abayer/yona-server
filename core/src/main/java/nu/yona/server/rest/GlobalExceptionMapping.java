package nu.yona.server.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.Translator;
import nu.yona.server.exceptions.YonaException;

/**
 * This class contains the mapping for the different exceptions and how they should be mapped to an http response
 * 
 * @author pgussow
 */
@ControllerAdvice
public class GlobalExceptionMapping
{
	@Autowired
	Translator translator;

	/**
	 * This method generically handles the illegal argument exceptions. They are translated into nice ResponseMessage objects so
	 * the response data is properly organized and JSON parseable.
	 * 
	 * @param ide The exception
	 * @return The response object to return.
	 */
	@ExceptionHandler(YonaException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public ResponseMessageDTO handle(YonaException ide)
	{
		return new ResponseMessageDTO(ResponseMessageType.ERROR, ide.getMessageId(),
				translator.getLocalizedMessage(ide.getMessageId(), ide.getParameters()));
	}
}
