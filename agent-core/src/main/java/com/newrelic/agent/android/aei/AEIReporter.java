/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.google.gson.JsonSyntaxException;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class AEIReporter extends PayloadReporter {
    protected static AtomicReference<AEIReporter> instance = new AtomicReference<>(null);

    public static AEIReporter getInstance() {
        return instance.get();
    }

    public static AEIReporter initialize(AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new AEIReporter(agentConfiguration));
        Harvest.addHarvestListener(instance.get());
        return instance.get();
    }

    public static void shutdown() {
        if (isInitialized()) {
            instance.get().stop();
            instance.set(null);
        }
    }

    protected static boolean isInitialized() {
        return instance.get() != null;
    }

    protected AEIReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
    }

    @Override
    public void start() {
        if (isInitialized()) {
            if (isStarted.compareAndSet(false, true)) {

            }
        } else {
            log.error("AEIReporter: Must initialize PayloadController first.");
        }
    }

    @Override
    protected void stop() {
    }

    protected void reportSavedAEI() {
        AEI aei = new AEI();
        ArrayList<String> listOfAEI = new ArrayList<>();
        listOfAEI = aei.getListOfAEI();
        for(int i = 0; i < listOfAEI.size(); i++) {
            File aeiFile = new File(listOfAEI.get(i));
            String aeiTraceReport = aeifileToString(aeiFile);;
            reportAEI(aeiTraceReport, i);
        }
    }

    protected String aeifileToString(File aeiFile){
        StringBuilder str = new StringBuilder();
        try (BufferedReader reader = Streams.newBufferedFileReader(aeiFile)) {
            reader.lines().forEach(s -> {
                if (!(null == s || s.isEmpty())) {
                    try {
                        str.append(s);
                    } catch (JsonSyntaxException e) {
                        log.error("Invalid Json entry skipped [" + s + "]");
                    }
                }
            });
        }catch(Exception ex){
            ex.printStackTrace();
        }

        return str.toString();
    }

    protected Future reportAEI(String aei, int position) {
        final boolean hasValidDataToken = Harvest.getHarvestConfiguration().getDataToken().isValid();

            if (hasValidDataToken) {
                if (aei != null) {
                    final AEISender sender = new AEISender(aei, agentConfiguration);

                    long aeiSize = aei.toString().getBytes().length;
                    if (aeiSize > Constants.Network.MAX_PAYLOAD_SIZE) {
                        DeviceInformation deviceInformation = Agent.getDeviceInformation();
                        String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                                .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                                .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                                .replace(MetricNames.TAG_SUBDESTINATION, "mobile_crash");
                        StatsEngine.notice().inc(name);
                        //delete AEI here?
                        log.error("Unable to upload crashes because payload is larger than 1 MB, crash report is discarded.");
                        return null;
                    }

                    final PayloadSender.CompletionHandler completionHandler = new PayloadSender.CompletionHandler() {
                        @Override
                        public void onResponse(PayloadSender payloadSender) {
                            if (payloadSender.isSuccessfulResponse()) {
                                //delete AEI here
                                AEI aei = new AEI();
                                ArrayList<String> listOfAEI = new ArrayList<>();
                                listOfAEI = aei.getListOfAEI();
                                listOfAEI.remove(position);

                                //add supportability metrics
                                DeviceInformation deviceInformation = Agent.getDeviceInformation();
                                String name = MetricNames.SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES
                                        .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                                        .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                                        .replace(MetricNames.TAG_SUBDESTINATION, "mobile_crash");
                                StatsEngine.get().sampleMetricDataUsage(name, aei.toString().getBytes().length, 0);
                            } else {
                                //Offline storage: No network at all, don't send back data
                                if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                                    log.warn("AEIReporter didn't send due to lack of network connection");
                                }
                            }
                        }

                        @Override
                        public void onException(PayloadSender payloadSender, Exception e) {
                            log.error("AEIReporter: AEI upload failed: " + e);
                        }
                    };

                    if (!sender.shouldUploadOpportunistically()) {
                        log.warn("AEIReporter: network is unreachable. AEI will be uploaded on next app launch");
                    }

                    return PayloadController.submitPayload(sender, completionHandler);
                } else {
                    log.warn("AEIReporter: attempted to report null AEI.");
                }

            } else {
                log.warn("AEIReporter: agent has not successfully connected and cannot report AEI.");
            }

        return null;
    }

    @Override
    public void onHarvest() {
        PayloadController.submitCallable(new Callable() {
            @Override
            public Void call() {
                reportSavedAEI();
                return null;
            }
        });
    }

}
