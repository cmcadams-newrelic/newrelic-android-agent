package com.newrelic.agent.android.aei;

import java.util.ArrayList;

public class AEI {
    private static ArrayList<String> listOfAEI = new ArrayList<>();

    public AEI(){
        super();
    }

    public AEI(String filePath){
        listOfAEI.add(filePath);
    }

    public ArrayList<String> getListOfAEI() {
        return listOfAEI;
    }

    public void setListOfAEI(ArrayList<String> listOfAEI) {
        this.listOfAEI = listOfAEI;
    }
}
