package poc;

public final class UserInfo {
    public long timestamp;
    public String name;
    public String seed;
    public String hash;

    public UserInfo(long timestamp, String name, String seed, String hash) {
        this.timestamp = timestamp;
        this.name = name;
        this.seed = seed;
        this.hash = hash;
    }

    public String toJTR() {
        String temp = this.hash.substring(this.hash.indexOf(':') + 1);
        String out = this.name + ":" + temp;
        return out;
    }

}

