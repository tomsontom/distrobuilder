package at.bestsolution.releng.distrobuilder;

public class InstallUnit implements FilteredElement {

	private String name;
	private String version;
	private String os;
	private String arch;

	public InstallUnit() {
	}
	
	public InstallUnit(String featureName, String version, String os, String arch) {
		this.name = featureName;
		this.version = version;
		this.os = os;
		this.arch = arch;
	}
	
	
	public String getName() {
		return name;
	}


	public void setName(String name) {
		this.name = name;
	}


	public String getVersion() {
		return version;
	}


	public void setVersion(String version) {
		this.version = version;
	}


	public String getOs() {
		return os;
	}


	public void setOs(String os) {
		this.os = os;
	}


	public String getArch() {
		return arch;
	}


	public void setArch(String arch) {
		this.arch = arch;
	}

	@Override
	public String getValue() {
		return getName();
	}
}