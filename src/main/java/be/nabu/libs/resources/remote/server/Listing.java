package be.nabu.libs.resources.remote.server;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "list")
public class Listing {

	private String path;
	private List<Entry> entries = new ArrayList<Entry>();

	public List<Entry> getEntries() {
		return entries;
	}

	public void setEntries(List<Entry> entries) {
		this.entries = entries;
	}

	@XmlAttribute
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
}
