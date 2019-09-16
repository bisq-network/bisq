package bisq.core.xmr.trade;

import java.io.Serializable;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO(niyid) In development
/*
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
*/

import bisq.core.user.Preferences;

/*@Aspect
@Component
@Configuration
@EnableAspectJAutoProxy*/
public class XmrTradeAutomationInterceptor implements Serializable {

	protected final Logger log = LoggerFactory.getLogger(XmrTradeAutomationInterceptor.class);
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -603802504694339146L;
	private Preferences preferences;
	
	@Inject
	public XmrTradeAutomationInterceptor(Preferences preferences) {
		this.preferences = preferences;
	}

/*
	@Around("@annotation(LogExecutionTime)")
	public Object transferFundsToSeller(ProceedingJoinPoint joinPoint) throws Throwable {
		log.debug("transferFundsToSeller({})", joinPoint.getArgs());
		
		//TODO Get trade object (an instance of SellerAsMakerTrade or BuyerAsMakerTrade) and check the 
		//TODO Check if trade automation is active - PreferencesPayload.useBisqXmrWallet = true
		//TODO Check if wallet balance can cover trade
		//TODO If balance enough, do transfer
		
		Object returnValue = joinPoint.proceed();
		
		return returnValue;
	}
*/
}
