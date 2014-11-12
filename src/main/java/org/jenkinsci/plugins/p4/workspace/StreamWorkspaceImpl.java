package org.jenkinsci.plugins.p4.workspace;

import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.IStream;
import com.perforce.p4java.core.IStreamSummary;
import com.perforce.p4java.impl.mapbased.client.Client;
import com.perforce.p4java.option.server.GetStreamsOptions;
import com.perforce.p4java.server.IOptionsServer;

public class StreamWorkspaceImpl extends Workspace {

	private final String streamName;
	private final String format;

	private static Logger logger = Logger.getLogger(StreamWorkspaceImpl.class
			.getName());

	public String getStreamName() {
		return streamName;
	}

	public String getFormat() {
		return format;
	}

	@Override
	public String getName() {
		return format;
	}

	@Override
	public WorkspaceType getType() {
		return WorkspaceType.STREAM;
	}

	@DataBoundConstructor
	public StreamWorkspaceImpl(String charset, String streamName, String format) {
		super(charset);
		this.streamName = streamName;
		this.format = format;
	}

	@Override
	public IClient setClient(IOptionsServer connection, String user)
			throws Exception {
		// expands Workspace name if formatters are used.
		String clientName = getFullName();
				
		IClient iclient = connection.getClient(clientName);
		if (iclient == null) {
			logger.info("P4: Creating stream client: " + clientName);
			Client implClient = new Client(connection);
			implClient.setName(clientName);
			connection.createClient(implClient);
			iclient = connection.getClient(clientName);
		}
		// Set owner (not set during create)
		iclient.setOwnerName(user);
		iclient.setStream(getStreamName());
		return iclient;
	}

	@Extension
	public static final class DescriptorImpl extends WorkspaceDescriptor {

		public static final String defaultFormat = "jenkins-${NODE_NAME}-${JOB_NAME}";

		@Override
		public String getDisplayName() {
			return "Streams (view generated by Perforce for each node)";
		}

		/**
		 * Provides auto-completion for workspace names. Stapler finds this
		 * method via the naming convention.
		 * 
		 * @param value
		 *            The text that the user entered.
		 */
		public AutoCompletionCandidates doAutoCompleteStreamName(
				@QueryParameter String value) {

			AutoCompletionCandidates c = new AutoCompletionCandidates();
			try {
				IOptionsServer iserver = ConnectionFactory.getConnection();
				if (iserver != null && value.length() > 1) {
					List<String> streamPaths = new ArrayList<String>();
					streamPaths.add(value + "...");
					GetStreamsOptions opts = new GetStreamsOptions();
					opts.setMaxResults(10);
					List<IStreamSummary> list = iserver.getStreams(streamPaths,
							opts);
					for (IStreamSummary l : list) {
						c.add(l.getStream());
					}
				}
			} catch (Exception e) {
			}

			return c;
		}

		public FormValidation doCheckStreamName(@QueryParameter String value) {
			try {
				IOptionsServer p4 = ConnectionFactory.getConnection();
				IStream stream = p4.getStream(value);
				if (stream != null) {
					return FormValidation.ok();
				}
				return FormValidation.error("Unknown Stream: " + value);
			} catch (Exception e) {
				return FormValidation.error(e.getMessage());
			}
		}

		public FormValidation doCheckFormat(@QueryParameter final String value) {
			if (value == null || value.isEmpty())
				return FormValidation
						.error("Workspace Name format is mandatory.");

			if (value.contains("${") && value.contains("}")) {
				return FormValidation.ok();
			}
			return FormValidation.error("Workspace Name format error.");
		}
	}
}