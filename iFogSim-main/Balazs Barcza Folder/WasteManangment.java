package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation  - Smart Waste Management System
 * @author Balazs Barcza
 *
 */
public class WasteManangment {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();  //  array hold how many fog devices we create in the simulation
	static List<Sensor> sensors = new ArrayList<Sensor>();           //  array hold sensors
	static List<Actuator> actuators = new ArrayList<Actuator>();      // an intervening element capable of exerting an effect corresponding to some control signal.
	static int numOfAreas = 1;                                        // number of the City District
	static int numOfBinPerArea = 4;                                   // how many bins has the area
	
	//private static boolean CLOUD = false;  // the cloud switch on and off
	private static boolean CLOUD = true;
	
	public static void main(String[] args) {    // main class control the process

		Log.printLine("Starting Smart Waste Management System...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "Starting Smart Waste Management System"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("m")){ // names of all Smart Bin start with 'm' 
					moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Bin
				}
			}
			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("object_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("object_tracker", "cloud"); // placing all instances of Object Tracker module in the Cloud
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();   // running the simulation function

			CloudSim.stopSimulation();    // stop the simulation function


			Log.printLine("Smart Waste Management System finished!");
		} catch (Exception e) {       // we can catch any error when the simulation happening and we print out
			e.printStackTrace();
			//System.out.println(e.toString());
			Log.printLine("Unwanted errors happen");
		}
	}
	
	
/**
 * Creates the fog devices in the physical topology of the simulation.
 * @param userId
 * @param appId
 */

//Level 0 Cloud + Level 1 Proxy Server
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 7500	, 64, 1000 , 1000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 7000	, 32, 500 , 500, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			cellularNetwork(i+"", userId, appId, proxy.getId());
		}
	}

	//Level 1 cellular Network with edge server
		private static FogDevice cellularNetwork (String id, int userId, String appId, int parentId){
			FogDevice edgeServer= createFogDevice("edgeServer-"+id, 7000, 32 , 35, 35, 1, 0.0, 107.339, 83.4333);
			fogDevices.add(edgeServer);
			edgeServer.setUplinkLatency(2); // latency of connection between Cellular Network edgeserver and proxy server is 2 ms
			for(int i=0;i<numOfBinPerArea;i++){
				String mobileId = id+"-"+i;
				FogDevice smartBin = addSmartBin(mobileId, userId, appId, edgeServer.getId()); // adding a smart bin to the physical topology. Smart bin have been modeled as fog devices as well.
				smartBin.setUplinkLatency(2); // latency of connection between smart bin and Cellular Network edgeserver is 2 ms
				fogDevices.add(smartBin);
			}
			edgeServer.setParentId(parentId);
			return edgeServer;
		}
	// LEVEL 3 Smart bin + Sensors
	private static FogDevice addSmartBin(String id, int userId, String appId, int parentId){
		FogDevice smartBin = createFogDevice("MCU-"+id, 7000, 1000, 10000, 10000, 3, 0, 87.53, 82.44);
		smartBin.setParentId(parentId);
		System.out.println("ultrasonicSensor");
		Sensor ultrasonicSensor = new Sensor("HC-SR04"+id, "ULTRASONICSENSOR", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of "ULTRASONICSENSOR", (sensor) follows a deterministic distribution
		sensors.add(ultrasonicSensor);
		ultrasonicSensor.setGatewayDeviceId(smartBin.getId());
		ultrasonicSensor.setLatency(50.0);  // latency of connection between bin (sensor) and the parent Smart Bin is 1 ms
		
		System.out.println("motionSensor");
		Sensor motionSensor = new Sensor("PIR-motion-sensor"+id, "MOTIONSENSOR", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of "MOTIONSENSOR", (sensor) follows a deterministic distribution
		sensors.add(motionSensor);
		motionSensor.setGatewayDeviceId(smartBin.getId());
		motionSensor.setLatency(50.0);  // latency of connection between PTZ Control and the parent Smart Bin is 1 ms
		
		System.out.println("temperature");
		Sensor temperature = new Sensor("DHT22"+id, "TEMPERATURE", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of "TEMPERATURE", (sensor) follows a deterministic distribution
		sensors.add(temperature);
		temperature.setGatewayDeviceId(smartBin.getId());
		temperature.setLatency(50.0);  // latency of connection between bin (sensor) and the parent Smart Bin is 1 ms
		
		
		return smartBin;
	}
	
/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("user_interface", 10);
		// Balazs created those sensors
		application.addAppModule("temperature_detector", 10);
		application.addAppModule("ultrasonicSensor_detector", 10);
		application.addAppModule("motionSensor_detector", 10);
				
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		
		// Balazs created those sensors
		application.addAppEdge("TEMPERATURE", "temperature_detector", 1000, 20000, "TEMPERATURE", Tuple.UP, AppEdge.SENSOR); // adding edge from TEMPERATURE (sensor) to tempiture_detector module carrying tuples of type TEMPERATURE
		application.addAppEdge("ULTRASONICSENSOR", "ultrasonicSensor_detector", 1000, 20000, "ULTRASONICSENSOR", Tuple.UP, AppEdge.SENSOR); // adding edge from ULTRASONICSENSOR (sensor) to level_detector module carrying tuples of type ULTRASONICSENSOR
		application.addAppEdge("MOTIONSENSOR", "motionSensor_detector", 1000, 20000, "MOTIONSENSOR", Tuple.UP, AppEdge.SENSOR); // adding edge from MOTIONSENSOR (sensor) to Motion Detector module carrying tuples of type MOTIONSENSOR
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("temperature_detector", "TEMPERATURE", "TEMPERATURE", new FractionalSelectivity(1.0));
		application.addTupleMapping("ultrasonicSensor_detector", "ULTRASONICSENSOR", "ULTRASONICSENSOR", new FractionalSelectivity(1.0));
		application.addTupleMapping("motionSensor_detector", "MOTIONSENSOR", "MOTIONSENSOR", new FractionalSelectivity(1.0));
		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("temperature_detector");add("ultrasonicSensor_detector");add("motionSensor_detector");}});
		//final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("motionSensor_detector");add("temperature_detector");}});
		//List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		
		application.setLoops(loops);
		return application;
	}
}