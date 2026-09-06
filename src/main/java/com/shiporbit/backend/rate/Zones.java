package com.shiporbit.backend.rate;

import java.util.List;

public enum Zones {

        N1("North one", List.of("Delhi","Uttar Pradesh","Haryana","Rajasthan")),
        N2("North two", List.of("Chandigarh","Punjab","Himachal Pradesh","Uttarakhand","J&K","Ladakh")),
        E("East", List.of("West Bengal","Odisha","Bihar","Jharkhand","Chhattisgarh")),
        NE("North east", List.of("Assam","Arunachal Pradesh","Manipur","Meghalaya","Mizoram","Nagaland","Sikkim","Tripura")),
        W1("West one", List.of("Gujarat","Dadra & Nagar Haveli","Daman & Diu")),
        W2("West two", List.of("Maharashtra","Goa")),
        S1("South one", List.of("Andhra Pradesh", "Karnataka","Tamil Nadu","Telangana","Puducherry")),
        S2("South two", List.of("Kerala")),
        C("Central", List.of("Madhya Pradesh"));


        private final String name;
        private final List<String> zoneList;


        Zones(String name, List<String> zoneList) {
            this.name = name;
            this.zoneList = zoneList;
        }

        public String getName(){
            return name;
        }

        public List<String> getZoneList() {
            return zoneList;
        }

        /**
         * Resolves the Zone a given state/UT name belongs to (case-insensitive, exact match
         * against the state names above). Throws if the state isn't mapped to any zone yet.
         */
        public static Zones fromState(String state) {
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("State is required to resolve a zone");
            }
            String normalized = state.trim();
            for (Zones zone : values()) {
                for (String candidate : zone.zoneList) {
                    if (candidate.equalsIgnoreCase(normalized)) {
                        return zone;
                    }
                }
            }
            throw new IllegalArgumentException("No zone mapped for state: " + state);
        }
}
