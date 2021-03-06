package com.marginallyclever.makelangeloRobot.settings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.prefs.Preferences;

import com.marginallyclever.makelangelo.Log;
import com.marginallyclever.makelangeloRobot.settings.hardwareProperties.Makelangelo2Properties;
import com.marginallyclever.makelangeloRobot.settings.hardwareProperties.MakelangeloHardwareProperties;
import com.marginallyclever.util.PreferencesHelper;


/**
 * TODO move tool names into translations & add a color palette system for quantizing colors
 * All the hardware settings for a single Makelangelo robot.
 * @author Dan Royer
 */
public final class MakelangeloRobotSettings {
	public static final double INCH_TO_CM = 2.54;
	/**
	 * measured from "m4 calibration.pdf"
	 * @since 7.5.0
	 */
	public static final float CALIBRATION_MM_FROM_TOP = 217f;
	
	private DecimalFormat df;
	
	private String[] configsAvailable;
	

	private ArrayList<MakelangeloRobotSettingsListener> listeners;

	// Each robot has a global unique identifier
	private long robotUID;
	// if we wanted to test for Marginally Clever brand Makelangelo robots
	private boolean isRegistered;
	// machine physical limits, in mm
	private double limitLeft;
	private double limitRight;
	private double limitBottom;
	private double limitTop;
	// paper area, in mm
	private double paperLeft;
	private double paperRight;
	private double paperBottom;
	private double paperTop;
	// % from edge of paper.
	private double paperMargin;

	private boolean reverseForGlass;
	// for a while the robot would sign it's name at the end of a drawing
	private boolean shouldSignName;
	
	private int hardwareVersion;
	private MakelangeloHardwareProperties hardwareProperties;

	private Color paperColor;

	/**
	 * pen diameter, in mm
	 */
	protected float diameter;
	/**
	 * pen up servo angle
	 */
	protected float zOff;
	/**
	 * pen down servo angle
	 */
	protected float zOn;
	/**
	 * pen servo movement speed
	 */
	protected float zRate;
	
	protected Color penDownColorDefault;
	protected Color penDownColor;
	protected Color penUpColor;
	// speed control
	private float maxFeedRate;
	private float currentFeedRate;
	private float maxAcceleration;
	
	/**
	 * top left, bottom center, etc...
	 *
	 * <pre>
	 * {@code private String[] startingStrings =  {
	 *       "Top Left",
	 *       "Top Center",
	 *       "Top Right",
	 *       "Left",
	 *       "Center",
	 *       "Right",
	 *       "Bottom Left",
	 *       "Bottom Center",
	 *       "Bottom Right"
	 *   };}
	 * </pre>
	 */
	private int startingPositionIndex;

	private final Preferences topLevelMachinesPreferenceNode = PreferencesHelper.getPreferenceNode(PreferencesHelper.MakelangeloPreferenceKey.MACHINES);

	
	/**
	 * These values should match https://github.com/marginallyclever/makelangelo-firmware/firmware_rumba/configure.h
	 *
	 * @param translator
	 * @param robot
	 */
	public MakelangeloRobotSettings() {
		// set up number format
		DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols();
		otherSymbols.setDecimalSeparator('.');
		df = new DecimalFormat("#.###",otherSymbols);
		df.setGroupingUsed(false);
				
		double mh = 835; // mm
		double mw = 835; // mm
		
		robotUID = 0;
		isRegistered = false;
		limitTop = mh/2;
		limitBottom = -mh/2;
		limitRight = mw/2;
		limitLeft = -mw/2;

		paperColor = Color.WHITE;
		
		listeners = new ArrayList<MakelangeloRobotSettingsListener>();
		shouldSignName = false;
		
		// paper area
		double pw = 420 * 0.1; // cm
		double ph = 594 * 0.1; // cm

		paperTop = ph/2;
		paperBottom = -ph/2;
		paperLeft = -pw/2;
		paperRight = pw/2;
		paperMargin = 0.9;

		maxFeedRate = 6500;
		currentFeedRate = 6500;
		maxAcceleration = 2500;

		// pen
		diameter = 0.8f;
		zRate = 50;
		zOn = 50;
		zOff = 90;
		penDownColor = penDownColorDefault = Color.BLACK;
		penUpColor = Color.BLUE;
		reverseForGlass = false;
		startingPositionIndex = 4;

		// default hardware version is 2
		setHardwareVersion(2);
		
		// which configurations are available?
		try {
			configsAvailable = topLevelMachinesPreferenceNode.childrenNames();
		} catch (Exception e) {
			Log.error( e.getMessage() );
			configsAvailable = new String[1];
			configsAvailable[0] = "Default";
		}
		
		// Load most recent config
		//loadConfig(last_machine_id);
	}
	
	public void addListener(MakelangeloRobotSettingsListener listener) {
		listeners.add(listener);
	}
	
	public void createNewUID(long newUID) {
		// make sure a topLevelMachinesPreferenceNode node is created
		topLevelMachinesPreferenceNode.node(Long.toString(newUID));
		
		// if this is a new robot UID, update the list of available configurations
		final String[] new_list = new String[configsAvailable.length + 1];
		System.arraycopy(configsAvailable, 0, new_list, 0, configsAvailable.length);
		new_list[configsAvailable.length] = Long.toString(newUID);
		configsAvailable = new_list;
	}
	
	
	
	public double getAcceleration() {
		return maxAcceleration;
	}

	/**
	 * Get the UID of every machine this computer recognizes INCLUDING machine 0, which is only assigned temporarily when a machine is new or before the first software connect.
	 *
	 * @return an array of strings, each string is a machine UID.
	 */
	public String[] getAvailableConfigurations() {
		return configsAvailable;
	}

	/**
	 * @return home X coordinate in mm
	 */ 
	public double getHomeX() {
		return 0;
	}
	
	/**
	 * @return home Y coordinate in mm
	 */
	public double getHomeY() {
		if(this.hardwareVersion==6) {
			// Zarplotter
			// 2017-08-13 DR this solution is janky as fuck, hardware version shouldn't be mentioned at this level
			return 0;
		} else {
			float limitTop = (float)getLimitTop();
			float homeY = limitTop - MakelangeloRobotSettings.CALIBRATION_MM_FROM_TOP;
			homeY = (float)Math.floor(homeY*1000.0f)/1000.0f;
			return homeY;
		}
	}
	
	public String getGCodeSetPositionAtHome() {
		return "G92 X"+getHomeX()+" Y"+getHomeY();
	}

	public String getGCodeConfig() {
		String result;
		String xAxis = "M101 A0 T"+df.format(limitRight)+" B"+df.format(limitLeft);
		String yAxis = "M101 A1 T"+df.format(limitTop)+" B"+df.format(limitBottom);
		String zAxis = "M101 A2 T170 B10";
		result = xAxis+"\n"+yAxis+"\n"+zAxis; 
		return result;
	}


	public String getAbsoluteMode() {
		return "G90";
	}

	public String getRelativeMode() {
		return "G91";
	}


	public int getKnownMachineIndex() {
		String [] list = getKnownMachineNames();
		for (int i = 0; i < list.length; i++) {
			if (list[i].equals("0")) continue;
			if (list[i].equals(Long.toString(robotUID))) {
				return i;
			}
		}

		return -1;
	}

	/*
  // TODO finish these cloud storage methods.  Security will be a problem.

   public boolean GetCanUseCloud() {
    return topLevelMachinesPreferenceNode.getBoolean("can_use_cloud", false);
  }


  public void SetCanUseCloud(boolean b) {
    topLevelMachinesPreferenceNode.putBoolean("can_use_cloud", b);
  }

  protected boolean SaveConfigToCloud() {
    return false;
  }



   protected boolean LoadConfigFromCloud() {
     // Ask for credentials: MC login, password.  auto-remember login name.
     //String login = new String();
     //String password = new String();

     //try {
     // Send query
     //URL url = new URL("https://marginallyclever.com/drawbot_getmachineconfig.php?name="+login+"pass="+password+"&id="+robot_uid);
     //URLConnection conn = url.openConnection();
     //BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
     // read data

     // close connection
     //rd.close();
     //} catch (Exception e) {}

    return false;
  }
	 */


	/**
	 * Get the UID of every machine this computer recognizes EXCEPT machine 0, which is only assigned temporarily when a machine is new or before the first software connect.
	 *
	 * @return an array of strings, each string is a machine UID.
	 */
	public String[] getKnownMachineNames() {
		final List<String> knownMachineList = new LinkedList<>(Arrays.asList(configsAvailable));
		if (knownMachineList.contains("0")) {
			knownMachineList.remove("0");
		}
		return Arrays.copyOf(knownMachineList.toArray(), knownMachineList.size(), String[].class);
	}


	/**
	 * @return bottom limit in mm
	 */
	public double getLimitBottom() {
		return limitBottom;
	}


	/**
	 * @return left limit in mm
	 */
	public double getLimitLeft() {
		return limitLeft;
	}


	/**
	 * @return right limit in mm
	 */
	public double getLimitRight() {
		return limitRight;
	}

	/**
	 * @return top limit in mm
	 */
	public double getLimitTop() {
		return limitTop;
	}

	/**
	 * @return the number of machine configurations that exist on this computer
	 */
	public int getMachineCount() {
		return configsAvailable.length;
	}


	/**
	 * @return paper bottom edge in mm.
	 */
	public double getPaperBottom() {
		return paperBottom;
	}


	/**
	 * @return paper height in mm.
	 */
	public double getPaperHeight() {
		return paperTop - paperBottom;
	}

	/**
	 * @return paper left edge in mm.
	 */
	public double getPaperLeft() {
		return paperLeft;
	}


	/**
	 * @return paper margin %.
	 */
	public double getPaperMargin() {
		return paperMargin;
	}


	/**
	 * @return paper right edge in mm.
	 */
	public double getPaperRight() {
		return paperRight;
	}


	/**
	 * @return paper top edge in mm.
	 */
	public double getPaperTop() {
		return paperTop;
	}


	/**
	 * @return paper width in mm.
	 */
	public double getPaperWidth() {
		return paperRight - paperLeft;
	}
	
	public long getUID() {
		return robotUID;
	}

	public boolean isPaperConfigured() {
		return (paperTop > paperBottom && paperRight > paperLeft);
	}

	public boolean isRegistered() {
		return isRegistered;
	}

	public boolean isReverseForGlass() {
		return reverseForGlass;
	}

	/**
	 * Load the machine configuration
	 *
	 * @param uid the unique id of the robot to be loaded
	 */
	public void loadConfig(long uid) {
		robotUID = uid;
		// once cloud logic is finished.
		//if( GetCanUseCloud() && LoadConfigFromCloud() ) return;
		loadConfigFromLocal();
	}

	protected void loadConfigFromLocal() {
		final Preferences uniqueMachinePreferencesNode = topLevelMachinesPreferenceNode.node(Long.toString(robotUID));
		limitTop    = Double.valueOf(uniqueMachinePreferencesNode.get("limit_top", Double.toString(limitTop)));
		limitBottom = Double.valueOf(uniqueMachinePreferencesNode.get("limit_bottom", Double.toString(limitBottom)));
		limitLeft   = Double.valueOf(uniqueMachinePreferencesNode.get("limit_left", Double.toString(limitLeft)));
		limitRight  = Double.valueOf(uniqueMachinePreferencesNode.get("limit_right", Double.toString(limitRight)));

		paperLeft   = Double.parseDouble(uniqueMachinePreferencesNode.get("paper_left",Double.toString(paperLeft)));
		paperRight  = Double.parseDouble(uniqueMachinePreferencesNode.get("paper_right",Double.toString(paperRight)));
		paperTop    = Double.parseDouble(uniqueMachinePreferencesNode.get("paper_top",Double.toString(paperTop)));
		paperBottom = Double.parseDouble(uniqueMachinePreferencesNode.get("paper_bottom",Double.toString(paperBottom)));

		maxAcceleration=Float.valueOf(uniqueMachinePreferencesNode.get("acceleration",Float.toString(maxAcceleration)));

		startingPositionIndex = Integer.valueOf(uniqueMachinePreferencesNode.get("startingPosIndex",Integer.toString(startingPositionIndex)));

		int r,g,b;
		r = uniqueMachinePreferencesNode.getInt("paperColorR", paperColor.getRed());
		g = uniqueMachinePreferencesNode.getInt("paperColorG", paperColor.getGreen());
		b = uniqueMachinePreferencesNode.getInt("paperColorB", paperColor.getBlue());
		paperColor = new Color(r,g,b);

		paperMargin = Double.valueOf(uniqueMachinePreferencesNode.get("paper_margin", Double.toString(paperMargin)));
		reverseForGlass = Boolean.parseBoolean(uniqueMachinePreferencesNode.get("reverseForGlass", Boolean.toString(reverseForGlass)));
		//setCurrentToolNumber(Integer.valueOf(uniqueMachinePreferencesNode.get("current_tool", Integer.toString(getCurrentToolNumber()))));
		setRegistered(Boolean.parseBoolean(uniqueMachinePreferencesNode.get("isRegistered",Boolean.toString(isRegistered))));

		loadPenConfig(uniqueMachinePreferencesNode);

		setHardwareVersion(Integer.parseInt(uniqueMachinePreferencesNode.get("hardwareVersion", Integer.toString(hardwareVersion))));
	}

	protected void loadPenConfig(Preferences prefs) {
		prefs = prefs.node("Pen");
		setDiameter(Float.parseFloat(prefs.get("diameter", Float.toString(diameter))));
		zRate = Float.parseFloat(prefs.get("z_rate", Float.toString(zRate)));
		zOn = Float.parseFloat(prefs.get("z_on", Float.toString(zOn)));
		zOff = Float.parseFloat(prefs.get("z_off", Float.toString(zOff)));
		//tool_number = Integer.parseInt(prefs.get("tool_number",Integer.toString(tool_number)));
		maxFeedRate = Float.parseFloat(prefs.get("feed_rate", Float.toString(maxFeedRate)));
		currentFeedRate=Float.valueOf(prefs.get("feed_rate_current",Float.toString(currentFeedRate)));
		
		int r,g,b;
		r = prefs.getInt("penDownColorR", penDownColor.getRed());
		g = prefs.getInt("penDownColorG", penDownColor.getGreen());
		b = prefs.getInt("penDownColorB", penDownColor.getBlue());
		penDownColor = penDownColorDefault = new Color(r,g,b);
		r = prefs.getInt("penUpColorR", penUpColor.getRed());
		g = prefs.getInt("penUpColorG", penUpColor.getGreen());
		b = prefs.getInt("penUpColorB", penUpColor.getBlue());
		penUpColor = new Color(r,g,b);
	}

	protected void savePenConfig(Preferences prefs) {
		prefs = prefs.node("Pen");
		prefs.put("diameter", Float.toString(getPenDiameter()));
		prefs.put("z_rate", Float.toString(zRate));
		prefs.put("z_on", Float.toString(zOn));
		prefs.put("z_off", Float.toString(zOff));
		//prefs.put("tool_number", Integer.toString(toolNumber));
		prefs.put("feed_rate", Float.toString(maxFeedRate));
		prefs.put("feed_rate_current", Float.toString(currentFeedRate));
		prefs.putInt("penDownColorR", penDownColorDefault.getRed());
		prefs.putInt("penDownColorG", penDownColorDefault.getGreen());
		prefs.putInt("penDownColorB", penDownColorDefault.getBlue());
		prefs.putInt("penUpColorR", penUpColor.getRed());
		prefs.putInt("penUpColorG", penUpColor.getGreen());
		prefs.putInt("penUpColorB", penUpColor.getBlue());
	}

	public void notifySettingsChanged() {
		for( MakelangeloRobotSettingsListener listener : listeners ) {
			listener.settingsChangedEvent(this);
		}
	}

	public void removeListener(MakelangeloRobotSettingsListener listener) {
		listeners.remove(listener);
	}
	
	// Save the machine configuration
	public void saveConfig() {
		// once cloud logic is finished.
		//if(GetCanUseCloud() && SaveConfigToCloud() ) return;
		saveConfigToLocal();
		notifySettingsChanged();
	}
	
	protected void saveConfigToLocal() {
		final Preferences uniqueMachinePreferencesNode = topLevelMachinesPreferenceNode.node(Long.toString(robotUID));
		uniqueMachinePreferencesNode.put("limit_top", Double.toString(limitTop));
		uniqueMachinePreferencesNode.put("limit_bottom", Double.toString(limitBottom));
		uniqueMachinePreferencesNode.put("limit_right", Double.toString(limitRight));
		uniqueMachinePreferencesNode.put("limit_left", Double.toString(limitLeft));
		uniqueMachinePreferencesNode.put("acceleration", Double.toString(maxAcceleration));
		uniqueMachinePreferencesNode.put("startingPosIndex", Integer.toString(startingPositionIndex));

		uniqueMachinePreferencesNode.putDouble("paper_left", paperLeft);
		uniqueMachinePreferencesNode.putDouble("paper_right", paperRight);
		uniqueMachinePreferencesNode.putDouble("paper_top", paperTop);
		uniqueMachinePreferencesNode.putDouble("paper_bottom", paperBottom);
		
		uniqueMachinePreferencesNode.putInt("paperColorR", paperColor.getRed());
		uniqueMachinePreferencesNode.putInt("paperColorG", paperColor.getGreen());
		uniqueMachinePreferencesNode.putInt("paperColorB", paperColor.getBlue());

		uniqueMachinePreferencesNode.put("paper_margin", Double.toString(paperMargin));
		uniqueMachinePreferencesNode.put("reverseForGlass", Boolean.toString(reverseForGlass));
		//uniqueMachinePreferencesNode.put("current_tool", Integer.toString(getCurrentToolNumber()));
		uniqueMachinePreferencesNode.put("isRegistered", Boolean.toString(isRegistered()));
		
		uniqueMachinePreferencesNode.put("hardwareVersion", Integer.toString(hardwareVersion));

		savePenConfig(uniqueMachinePreferencesNode);
	}
	
	public void setAcceleration(float f) {
		maxAcceleration = f;
	}
	
	public void setMaxFeedRate(float f) {
		maxFeedRate = f;
		if(currentFeedRate > maxFeedRate) {
			currentFeedRate = maxFeedRate;
		}
	}
	
	public float getMaxFeedRate() {
		return maxFeedRate;
	}
	
	public void setCurrentFeedRate(float f) {
		if (f < 0.001) f = 0.001f;
		if( f > maxFeedRate) {
			f = maxFeedRate;
		}
		currentFeedRate = f;
	}
	
	public float getCurrentFeedRate() {
		return currentFeedRate;
	}
	
	
	
	public void setLimitBottom(double limitBottom) {
		this.limitBottom = limitBottom;
	}
	
	public void setLimitLeft(double limitLeft) {
		this.limitLeft = limitLeft;
	}
	
	public void setLimitRight(double limitRight) {
		this.limitRight = limitRight;
	}
	
	public void setLimitTop(double limitTop) {
		this.limitTop = limitTop;
	}
	
	public void setMachineSize(double width, double height) {
		this.limitLeft = -width/2.0;
		this.limitRight = width/2.0;
		this.limitBottom = -height/2.0;
		this.limitTop = height/2.0;
	}
	
	public void setPaperMargin(double paperMargin) {
		this.paperMargin = paperMargin;	
	}

	public void setPaperSize(double width, double height) {
		this.paperLeft = -width/2;
		this.paperRight = width/2;
		this.paperTop = height/2;
		this.paperBottom = -height/2;
	}
	
	public void setRegistered(boolean isRegistered) {
		this.isRegistered = isRegistered;
	}
	
	public void setReverseForGlass(boolean reverseForGlass) {
		this.reverseForGlass = reverseForGlass;
	}
	
	public boolean shouldSignName() {
		return shouldSignName;
	}

	public int getHardwareVersion() {
		return hardwareVersion;
	}

	public MakelangeloHardwareProperties getHardwareProperties() {
		return hardwareProperties;
	}

	public void setHardwareVersion(int version) {
		int newVersion = -1;

		try {
			// get version numbers
			ServiceLoader<MakelangeloHardwareProperties> knownHardware = ServiceLoader.load(MakelangeloHardwareProperties.class);
			Iterator<MakelangeloHardwareProperties> i = knownHardware.iterator();
			while(i.hasNext()) {
				MakelangeloHardwareProperties hw = i.next();
				if(hw.getVersion()==version) {
					hardwareProperties = hw.getClass().newInstance();
					newVersion = version;
					break;
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			Log.error("Hardware version instance failed. Defaulting to v2");
			hardwareProperties = new Makelangelo2Properties();
			newVersion=2;
		}
		if(newVersion == -1) {
			Log.error("Unknown hardware version requested. Defaulting to v2");
			hardwareProperties = new Makelangelo2Properties();
		}
		
		hardwareVersion = newVersion;
		if(!hardwareProperties.canChangeMachineSize()) {
			this.setMachineSize(hardwareProperties.getWidth(), hardwareProperties.getHeight());
		}
	}
	
	public Color getPaperColor() {
		return paperColor;
	}
	
	public void setPaperColor(Color arg0) {
		paperColor = arg0;
	}
	
	public Color getPenDownColorDefault() {
		return penDownColorDefault;
	}
	
	public Color getPenDownColor() {
		return penDownColor;
	}
	
	public void setPenDownColorDefault(Color arg0) {
		penDownColorDefault=arg0;
	}
	
	public void setPenDownColor(Color arg0) {
		penDownColor=arg0;
	}
	
	public void setPenUpColor(Color arg0) {
		penUpColor=arg0;
	}
	
	public Color getPenUpColor() {
		return penUpColor;
	}

	public float getZRate() {
		return zRate;
	}
	
	public void setZRate(float arg0) {
		zRate = arg0;
	}
	
	/**
	 * @return pen diameter, in mm
	 */
	public float getPenDiameter() {
		return diameter;
	}

	public float getPenDownAngle() {
		return zOn;
	}

	public float getPenUpAngle() {
		return zOff;
	}

	public void setPenDownAngle(float arg0) {
		zOn=arg0;
	}

	public void setPenUpAngle(float arg0) {
		zOff=arg0;
	}

	public BasicStroke getStroke() {
		return new BasicStroke(diameter * 10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	}

	public void setDiameter(float d) {
		diameter = d;
	}

	public String getPenDownString() {
		return "G01 F" + df.format(zRate) + " Z" + df.format(getPenDownAngle()) + ";\n";
	}

	public String getPenUpString() {
		return "G00 F" + df.format(zRate) + " Z" + df.format(getPenUpAngle()) + ";\n";
	}
	
	public String getMaxFeedrateString() {
		return "G00 F" + df.format(getMaxFeedRate()) + ";\n";
	}
	
	public String getCurrentFeedrateString() {
		return "G00 F" + df.format(getCurrentFeedRate()) + ";\n";
	}

	public void writeChangeTo(Writer out,Color newPenDownColor) throws IOException {
		penDownColor = newPenDownColor;
		writeChangeToInternal(out);
	}

	public void writeChangeTo(Writer out) throws IOException {
		penDownColor = penDownColorDefault;
		writeChangeToInternal(out);
	}
	
	protected void writeChangeToInternal(Writer out) throws IOException {
		int toolNumber = penDownColor.getRGB();
		out.write("M06 T" + toolNumber + ";\n");
		out.write("G00 F" + df.format(getCurrentFeedRate()) + " A" + df.format(getAcceleration()) + ";\n");
	}

	public void writeChangeTo(Writer out,String name) throws IOException {
		int toolNumber = penDownColor.getRGB();
		out.write("M06 T" + toolNumber + ";\n");
		//out.write("M117 Change to "+name+";\n");  // display message
		//out.write("M226;"); // gcode initiated pause
		out.write("G00 F" + df.format(getCurrentFeedRate()) + " A" + df.format(getAcceleration()) + ";\n");
	}

	public void writeMoveTo(Writer out, double x, double y,boolean isUp) throws IOException {
		String command;
		if(isUp) command="G00";
		else command="G01";
		out.write(command+" X" + df.format(x) + " Y" + df.format(y) + ";\n");
	}

	// lift the pen
	public void writePenUp(Writer out) throws IOException {
		out.write(getPenUpString());
		out.write("G00 F" + df.format(getMaxFeedRate()) + ";\n");
	}

	// lower the pen
	public void writePenDown(Writer out) throws IOException {
		out.write(getPenDownString());
		out.write("G01 F" + df.format(getCurrentFeedRate()) + ";\n");
	}
	
	public void writeAbsoluteMode(Writer out) throws IOException {
		out.write(getAbsoluteMode() + ";\n");
	}

	public void writeRelativeMode(Writer out) throws IOException {
		out.write(getRelativeMode() + ";\n");
	}
}
