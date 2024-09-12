package com.newrelic.agent.android.aeitrace;

import java.util.ArrayList;

public class AEITrace {
    private static ArrayList<String> listOfAEI = new ArrayList<>();

    public AEITrace(){
        super();
    }

    public AEITrace(String filePath){
        listOfAEI.add(filePath);
    }

    public ArrayList<String> getListOfAEI() {
        return listOfAEI;
    }

    public void setListOfAEI(ArrayList<String> listOfAEI) {
        this.listOfAEI = listOfAEI;
    }
}
