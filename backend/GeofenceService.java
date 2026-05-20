package backend;

public class GeofenceService {
    private static final double RAIO_TERRA_KM = 6371.0;

    public double calcularDistanciaMetros(double lat1, double lon1,
                                          double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RAIO_TERRA_KM * c * 1000;
    }

    public String verificarStatus(Localizacao loc, Fazenda fazenda) {
        double distancia = calcularDistanciaMetros(
                loc.getLatitude(), loc.getLongitude(),
                fazenda.getLatitudeCentro(), fazenda.getLongitudeCentro());
        double raio      = fazenda.getRaioMetros();
        double tolerancia = fazenda.getToleranciaMetros();

        if (distancia <= raio - tolerancia) return "Dentro";
        else if (distancia <= raio + tolerancia) return "Limite";
        else return "Fora";
    }

    public boolean estaDentro(Localizacao loc, Fazenda fazenda) {
        return "Dentro".equals(verificarStatus(loc, fazenda));
    }
}
