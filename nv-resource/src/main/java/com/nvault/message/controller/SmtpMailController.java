package com.nvault.message.controller;



import org.apache.velocity.runtime.directive.Foreach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.nvault.message.model.EMailSendStatus;
import com.nvault.message.model.Message;


import com.nvault.message.service.MessageService;
import com.nvault.model.NVaultUser;


@RestController
@RequestMapping("/mail")
public class SmtpMailController {
	
	@Autowired
	com.nvault.email.SmtpMailSender smtpMailSender;
	
	@Autowired
	MessageService messageService;

	
	@Value("${spring.mail.username}")
	String sender;

	@RequestMapping(value = "/send", method = RequestMethod.POST)
	public com.nvault.controller.Mail sendMail(@RequestBody com.nvault.controller.Mail mail) {
		
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
	   NVaultUser user = (NVaultUser)auth.getPrincipal();
 		
	   System.out.println(" \n came to smtp controller \n");

		try {
			String[] toAddress = new String[mail.getToAddress().size()];
			int i = 0;
			for (com.nvault.controller.EmailToAddress address : mail.getToAddress()) {
				toAddress[i] = address.getEmailId();
				i++;
			}
			String subject = mail.getSubject();
			String body = mail.getBody();

			Message message=new Message();
			
			message.setBody(mail.getBody());
			message.setSubject(mail.getSubject());
			
			String commaSeperatedToAddress="";
			
			for (String string : toAddress) {
				commaSeperatedToAddress += string+",";
			}
			
			commaSeperatedToAddress=commaSeperatedToAddress.substring(0,commaSeperatedToAddress.length()-1);

			message.setRecipient( commaSeperatedToAddress );
			message.setUser_id(user.getId());
			
			
			message.setSender(sender);
			
			message.setEmailSendStatus(EMailSendStatus.SENDBEGIN);
			
			
			// to save mail in db
			Message insertedMessage=messageService.saveMessage(message);
			
			smtpMailSender.send(toAddress, subject, body,insertedMessage);


		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
		return mail;
	}
	
}
