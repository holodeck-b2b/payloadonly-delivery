/**
 * Copyright (C) 2018 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.deliverymethod.file;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.common.util.MessageUnitUtils;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.interfaces.delivery.IMessageDeliverer;
import org.holodeckb2b.interfaces.delivery.IMessageDelivererFactory;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.events.IMessageDelivered;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;

/**
 * Provides a Holodeck B2B <i>delivery method</i> that will only write the payloads of a <i>User Message</i> to file.
 * This can be useful when the business documents contain all relevant meta-data that the back-end application needs
 * for processing the document.
 * <p>Because there is no ebMS meta-data written to file there is no possibility to indicate the possible relation
 * between multiple payloads in a <i>User Message</i> and it is the business applications responsibility to determine
 * the possible relations solely based on the contents of the payloads.
 * <p>This delivery method has one required parameter <i>deliveryDirectory</i> which indicates the path of the 
 * directory where the payload files should be written to. This directory must already exists and of course should be
 * accessible and writable by Holodeck B2B.
 * <p>NOTE 1: The back-end application should acquire a write lock on the files before processing them as Holodeck B2B
 * may still be writing data to them. 
 * <p>NOTE 2: As payloads are delivered individually the deliverer cannot ensure that the total delivery is atomic. 
 * The {@link IMessageDelivered} may be used to get notifications about the completeness of the delivery.
 * <p>NOTE 2: This delivery method can only be used for delivery of <i>User Message</i>. If used in a P-Mode that also
 * needs to notify signals (Receipts or Errors) to the back-end specific delivery methods should be specified!    
 * 
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class PayloadOnly implements IMessageDelivererFactory {
	
	private static Logger log = LogManager.getLogger(PayloadOnly.class);
	
	/**
	 * Path to the directory where the payloads should be stored
	 */
	private String	directory; 
	
	public void init(Map<String, ?> settings) throws MessageDeliveryException {
        if (settings != null) {
            try {
                directory = (String) settings.get("deliveryDirectory");
            } catch (final ClassCastException ex) {
            		log.error("No directory specified for the delivery of the paylaods! Check P-Mode configuration!");
                throw new MessageDeliveryException("No directory specified!");
            }
        }
        final Path path = !Utils.isNullOrEmpty(directory) ? Paths.get(directory) : null;
        // Test if given path exists and is a directory
        if (path == null || !Files.isDirectory(path) || !Files.isWritable(path)) {
        		log.error("The specified directory [{}] for delivery does not exits or is not writable!"
        				 + "Check P-Mode configuration!", directory);
            throw new MessageDeliveryException("Specified directory [" + directory
                                                                        + " does not exits or is not writable!");
        }
        
        // Ensure directory path ends with separator
        directory = (directory.endsWith(FileSystems.getDefault().getSeparator()) ? directory
                              : directory + FileSystems.getDefault().getSeparator());
		
        log.trace("Initialized delivery method succesfully");
	}

	public IMessageDeliverer createMessageDeliverer() throws MessageDeliveryException {
		return new PayloadOnlyDeliverer();
	}
	
	/**
	 * Is the actual {@link IMessageDeliverer} implementation that performs the delivery of the <i>User Message</i> to
	 * the file system.  
	 * 
	 * @author Sander Fieten (sander at holodeck-b2b.org)
	 */
	class PayloadOnlyDeliverer implements IMessageDeliverer {

		public void deliver(IMessageUnit rcvdMsgUnit) throws MessageDeliveryException {
			if (!(rcvdMsgUnit instanceof IUserMessage)) {
				log.error("Delivery request for a {} (mgsId={}). Please check P-Mode configuration!", 
						  MessageUnitUtils.getMessageUnitName(rcvdMsgUnit), rcvdMsgUnit.getMessageId());
				throw new MessageDeliveryException("Unsupported message unit type");
			}	
			final IUserMessage userMessage = (IUserMessage) rcvdMsgUnit;			
			// Get the messageId and first PartyId of the sender
			final String senderId = userMessage.getSender().getPartyIds().iterator().next().getId();
			final String messageId = rcvdMsgUnit.getMessageId();
						
			// If the message has no payloads, we can also not deliver it
			if (Utils.isNullOrEmpty(userMessage.getPayloads())) {
				log.error("User Message (msgId={}) does not contain any payloads for delivery!", messageId);
				throw new MessageDeliveryException("No payloads to deliver!");
			}			
			
			for (IPayload p : userMessage.getPayloads()) 
				deliverPayload(p, senderId, messageId);
		}

		/**
		 * Writes the content of the payload to the indicated directory. The MessageId and the (first) PartyId of the 
		 * sender of the message unit will be used to construct the file name. The PartyId of the sender is used as
		 * the MessageId might not be unique between different senders (although it should according to specs).   
		 *  
		 * @param p			The payload to save
		 * @param senderId	PartyId of the sender of the message 
		 * @param messageId	The MessageId
		 * @throws MessageDeliveryException	When the payload is not included in the message or when the payload data 
		 * 									cannot be written to file
		 */
		private void deliverPayload(final IPayload p, final String senderId, final String messageId) 
																				throws MessageDeliveryException {
	        /* If payload was external to message, it is not processed by Holodeck B2B, so no content to move. But
			   since there is also no meta-data to indicate a message is received we cannot deliver it as expected
			*/
	        if (IPayload.Containment.EXTERNAL == p.getContainment()) {
	        		log.error("Message contains external payload (href={}), cannot be delivered!", p.getPayloadURI());
	        		throw new MessageDeliveryException("Cannot deliver external payloads");
	        }
	        
	        final Path sourcePath = Paths.get(p.getContentLocation());
	        // Try to set nice extension based on MIME Type of payload
	        String mimeType = p.getMimeType();
	        if (Utils.isNullOrEmpty(mimeType)) {
	            // No MIME type given in message, try to detect from content
	            try { mimeType = Utils.detectMimeType(sourcePath.toFile()); }
	            catch (final IOException ex) { mimeType = null; } // Unable to detect the MIME Type
	        }
	        final String extension = Utils.getExtension(mimeType);	        
	        Path targetPath = null;	        
	        try {
	        	targetPath = Utils.createFileWithUniqueName(directory 
	        	 									+ (senderId + "-" + messageId).replaceAll("[^a-zA-Z0-9.]", "_") 
	        	 									+ extension);
	            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);	            
	        } catch (final IOException ex) {
	            // Can not move payload file -> delivery not possible
	        		log.error("Delivery failed because copy of payload file [{}] to delivery location [] failed!", 
	        					p.getContentLocation(), targetPath.toString());
	            // Try to remove the already created file
	            try {
	            		if (targetPath != null)
	            			Files.deleteIfExists(targetPath);
	            } catch (IOException io) {
	                log.error("Could not remove temp file [{}]! Please remove manually.", targetPath.toString());
	            }
	            throw new MessageDeliveryException("Could not copy of payload file to delivery location!", ex);
	        }			
		}
	}
}
