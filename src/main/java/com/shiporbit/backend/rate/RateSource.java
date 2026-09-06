package com.shiporbit.backend.rate;

public interface RateSource {

    double getRate(Zones sourceZone, Zones destZone, String destStateOrCity);

}
