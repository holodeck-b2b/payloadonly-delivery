package org.holodeckb2b.deliverymethod.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.holodeckb2b.common.messagemodel.ErrorMessage;
import org.holodeckb2b.common.messagemodel.PartyId;
import org.holodeckb2b.common.messagemodel.Payload;
import org.holodeckb2b.common.messagemodel.Receipt;
import org.holodeckb2b.common.messagemodel.UserMessage;
import org.holodeckb2b.common.mmd.xml.TradingPartner;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.general.IPartyId;
import org.holodeckb2b.interfaces.messagemodel.IPayload.Containment;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class PayloadOnlyDeliveryTest {

	private static final String	TEST_ROOT_DIR = new File(PayloadOnlyDeliveryTest.class.getClassLoader()
																.getResource(".").getPath()).getAbsolutePath();
	
	private static final String DELIVERY_DIR = TEST_ROOT_DIR + "/msg_in";
	private static final String PAYLOAD_DIR = TEST_ROOT_DIR + "/payloads/";	
	
	@BeforeClass
	public static void createDeliveryDirectory() {
		File deliveryDir = new File(DELIVERY_DIR);
		if (!deliveryDir.exists())
			deliveryDir.mkdir();				
	}
	
	@After
	public void cleanup() {
		for (File f : new File(DELIVERY_DIR).listFiles()) 
			f.delete();
	}
	
	@Test
	public void testNoDirectorySpecd() {
		PayloadOnly factory = new PayloadOnly();
		
		Map<String, String> params = new HashMap<>();
		params.put("deliveryDir", "not set");			
		try {
			factory.init(params);
			fail("Initialized without directory!");
		} catch (MessageDeliveryException mde) {
			// This is expected!
		}
	}

	@Test
	public void testNonExistingDirectory() {
		PayloadOnly factory = new PayloadOnly();
		
		Map<String, String> params = new HashMap<>();
		params.put("deliveryDirectory", "notthere");			
		try {
			factory.init(params);
			fail("Accepted non-existing directory!");
		} catch (MessageDeliveryException mde) {
			// This is expected!
		}
	}

	@Test
	public void testRejectSignalMessage() {
		PayloadOnly factory = new PayloadOnly();		
		Map<String, String> params = new HashMap<>();
		params.put("deliveryDirectory", DELIVERY_DIR);			
		try {
			factory.init(params);
		} catch (MessageDeliveryException mde) {
			fail("Rejected valid delivery directory!");
		}
		
		try {
			factory.createMessageDeliverer().deliver(new Receipt());
			fail("Should have rejected Receipt Signal message");
		} catch (MessageDeliveryException e) {
			// Okay
		}
		try {
			factory.createMessageDeliverer().deliver(new ErrorMessage());
			fail("Should have rejected Error Signal message");
		} catch (MessageDeliveryException e) {
			// Okay
		}		
	}
	
	@Test
	public void testDelivery() {
		PayloadOnly factory = new PayloadOnly();		
		Map<String, String> params = new HashMap<>();
		params.put("deliveryDirectory", DELIVERY_DIR);			
		try {
			factory.init(params);
		} catch (MessageDeliveryException mde) {
			fail("Rejected valid delivery directory!");
		}
		
		UserMessage userMessage = new UserMessage();
		final String messageId = "plod-test-messageid@holodeck-b2b.org";
		userMessage.setMessageId(messageId);
		final String senderId = "urn:org:holodeck-b2b:test:partner:0001";
		ArrayList<IPartyId> pids = new ArrayList<>(1);
		pids.add(new PartyId(senderId, null));
		TradingPartner sender = new TradingPartner();
		sender.setPartyIds(pids);
		userMessage.setSender(sender);
		Payload payload1 = new Payload();
		payload1.setContentLocation(PAYLOAD_DIR + "test-pl-1.xml");
		payload1.setContainment(Containment.BODY);
		userMessage.addPayload(payload1);
		Payload payload2 = new Payload();
		payload2.setContentLocation(PAYLOAD_DIR + "test-pl-2.jpg");
		payload2.setContainment(Containment.ATTACHMENT);
		userMessage.addPayload(payload2);
		
		try {
			factory.createMessageDeliverer().deliver(userMessage);
		} catch (MessageDeliveryException mde) {
			fail("Delivery of valid User Message failed!");
		}
		
		File[] deliveredPayloads = new File(DELIVERY_DIR).listFiles();
		assertEquals(2, deliveredPayloads.length);
		boolean foundXML = false; boolean foundJPG = false;
		for (File deliveredPayload : deliveredPayloads) {
			assertTrue(deliveredPayload.getName().startsWith(senderId.replaceAll("[^a-zA-Z0-9.]", "_")));
			assertTrue(deliveredPayload.getName().indexOf(messageId.replaceAll("[^a-zA-Z0-9.]", "_")) > 0);
			foundXML |= deliveredPayload.getName().endsWith("xml");
			foundJPG |= deliveredPayload.getName().endsWith("jpg");
		}
		assertTrue(foundXML);
		assertTrue(foundJPG);
	}
	
}
