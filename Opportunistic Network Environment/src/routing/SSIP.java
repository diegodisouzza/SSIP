package routing;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple;

public class SSIP extends ActiveRouter {
	public static final String SSIP = "SSIP";
	public static final String ssimPath = "ssimPath";
	public static final String crsPath = "crsPath";
	public static final String commPath = "commPath";
	
	protected Map<DTNHost, ArrayList<Double>> encounterTable = new HashMap<DTNHost, ArrayList<Double>>();
	private Map<Integer, Double> ssim = new HashMap<Integer, Double>();
	private Map<Integer, Double> crs = new HashMap<Integer, Double>();
	
	private Integer communityId;
	private Double ecrs;
	
	private String ssimFile;
	private String crsFile;
	private String commFile;
	private Boolean ssimRead = false;
	private Boolean crsRead = false;
	private Boolean commRead = false;
	
	FileReader fr;
	BufferedReader br;

	public SSIP(Settings s) {
		super(s);
		Settings settings = new Settings(SSIP);
		ssimFile = settings.getSetting(ssimPath);
		crsFile = settings.getSetting(crsPath);
		commFile = settings.getSetting(commPath);
	}

	public SSIP(SSIP s) {
		super(s);
		Settings settings = new Settings(SSIP);
		ssimFile = settings.getSetting(ssimPath);
		crsFile = settings.getSetting(crsPath);
		commFile = settings.getSetting(commPath);
	}

	public Double getEcrs() {
		return ecrs;
	}

	public void setEcrs() {
		Double sum = 0.0;
		
		for(Double value : crs.values()) {
			sum = sum + value;
		}
		
		ecrs = sum / crs.size();
	}

	public Integer getCommunityId() {
		return communityId;
	}
	
	public void setCommunityId() {
		try {
			fr = new FileReader(commFile);
			br = new BufferedReader(fr);
			
			String line = br.readLine();
			
			while(line != null) {
				String infos[] = line.split(" ");
				Integer node = Integer.parseInt(infos[0]);
				Integer comm = Integer.parseInt(infos[1]);
				
				if(node == this.getHost().getAddress()) {
					communityId = comm;
				}
				line = br.readLine();
			}
			br.close();
			
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public Double getCrs(DTNHost connected) {
		if(crs.containsKey(connected.getAddress())) {
			return crs.get(connected.getAddress());
		}
		else return 0.0;
	}

	public void setCrs() {
		try {
			fr = new FileReader(crsFile);
			br = new BufferedReader(fr);
			
			String line = br.readLine();
			
			while(line != null) {
				String infos[] = line.split(" ");
				Integer comm = Integer.parseInt(infos[0]);
				if(communityId==comm) {
					for(int i = 1; i < infos.length; i++) {
						crs.put(i, Double.parseDouble(infos[i]));
					}
				}
				line = br.readLine();
			}
			br.close();
			
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public Double getSsim(DTNHost connected) {
		if(ssim.containsKey(connected.getAddress())) {
			return ssim.get(connected.getAddress());
		}
		else return 0.0;
	}

	public void setSsim() {
		try {
			fr = new FileReader(ssimFile);
			br = new BufferedReader(fr);
			
			String line = br.readLine();
			
			while(line != null) {
				String infos[] = line.split(" ");
				Integer node_i = Integer.parseInt(infos[0]);
				Integer comm_i = Integer.parseInt(infos[1]);
				Integer node_j = Integer.parseInt(infos[2]);
				Integer comm_j = Integer.parseInt(infos[3]);
				Double ssim_value = Double.parseDouble(infos[4]);
				
				if(node_i == this.getHost().getAddress()) {
					ssim.put(node_j, ssim_value);
				}
				line = br.readLine();
			}
			br.close();
			
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	@Override
	public void changedConnection(Connection con)
	{
		if(con.isUp()) 
		{
			DTNHost connected = con.getOtherNode(this.getHost());
			encounterTimeRegister(connected);
		}
	}

	public void encounterTimeRegister(DTNHost connected) {
		if(encounterTable.containsKey(connected))
			encounterTable.get(connected).add(SimClock.getTime());
		else
		{
			ArrayList<Double> times = new ArrayList<Double>();
			times.add(SimClock.getTime());
			encounterTable.put(connected, times);
		}
	}

	public Tuple<Message, Connection> trySendMessages() {
		List<Tuple<Message, Connection>> messages = 
				new ArrayList<Tuple<Message, Connection>>(); 
		
		Collection<Message> msgCollection = getMessageCollection();
		for (Connection con : getConnections()) {
			DTNHost j_host = con.getOtherNode(getHost());
			SSIP j_router = (SSIP) j_host.getRouter();
			
			if (j_router.isTransferring()) {
				continue;
			}
			
			for (Message m : msgCollection) {
				if (j_router.hasMessage(m.getId())) {
					continue;
				}
				DTNHost d_host = m.getTo();
				SSIP d_router = (SSIP) d_host.getRouter();
				
				if(j_host.getAddress() == d_host.getAddress()) { // j == d
					messages.add(new Tuple<Message, Connection>(m,con));
				}
				else {
					if(this.communityId == d_router.getCommunityId()) { // comm(i) == comm(d)
						if(this.communityId == j_router.getCommunityId()) { // comm(i) == comm(j)
							if(j_router.getSsim(d_host) > this.getSsim(d_host)) { // ssim(j,d) > ssim(i,d)
								messages.add(new Tuple<Message, Connection>(m,con));
							}
						}
						else if(j_router.getCrs(d_host) > this.ecrs) { // crs(j,d) > ecrs
							messages.add(new Tuple<Message, Connection>(m,con));
						}
					}
					else { // comm(i) != comm(d)
						if(j_router.getCommunityId() == d_router.getCommunityId()) { // comm(j) == comm(d)
							messages.add(new Tuple<Message, Connection>(m,con));
						}
						else if(j_router.getCrs(d_host) > this.getCrs(d_host)) { // crs(j,d) > crs(i,d)
							messages.add(new Tuple<Message, Connection>(m,con));
						}
					}
				}
			}
		}
		
		if (messages.size() != 0)
			return tryMessagesForConnected(messages);
		else
			return null;
	}
	
	@Override
	public MessageRouter replicate() {
		return new SSIP(this);
	}

	@Override
	public void update() {
		super.update();
		if(!commRead) {
			commRead = true;
			setCommunityId();
		}
		if(!crsRead) {
			crsRead = true;
			setCrs();
		}
		if(ecrs==null) {
			setEcrs();
		}
		if(!ssimRead) {
			ssimRead = true;
			setSsim();
		}
		
		trySendMessages();
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		RoutingInfo ri = new RoutingInfo(this);
		RoutingInfo incoming = new RoutingInfo(this.incomingMessages.size() + 
				" incoming message(s)");
		RoutingInfo delivered = new RoutingInfo(this.deliveredMessages.size() +
				" delivered message(s)");
		
		RoutingInfo cons = new RoutingInfo(this.getConnections().size() + 
			" connection(s)");
		
		RoutingInfo ricrs = new RoutingInfo(this.crs.size() + " crs values");
		
		RoutingInfo rissim = new RoutingInfo(this.ssim.size() + " ssim values");
				
		ri.addMoreInfo(incoming);
		ri.addMoreInfo(delivered);
		ri.addMoreInfo(cons);
		ri.addMoreInfo(ricrs);
		ri.addMoreInfo(rissim);
		
		for (Message m : this.incomingMessages.values()) {
			incoming.addMoreInfo(new RoutingInfo(m));
		}
		
		for (Message m : this.deliveredMessages.values()) {
			delivered.addMoreInfo(new RoutingInfo(m + " path:" + m.getHops()));
		}
		
		for (Connection c : this.getConnections()) {
			cons.addMoreInfo(new RoutingInfo(c));
		}
		
		for (Integer comm : this.crs.keySet()) {
			ricrs.addMoreInfo(new RoutingInfo("crs between communities "+this.communityId+
					" and "+comm+" = "+this.crs.get(comm)));
		}
		
		for (Integer host : this.ssim.keySet()) {
			rissim.addMoreInfo(new RoutingInfo("ssim between nodes "+this.getHost().getAddress()+
					" "+host+" = "+this.ssim.get(host)));
		}

		return ri;
	}
}
