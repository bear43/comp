package entity;

public class Identifier {
    private int id;

    private int type;

    public Identifier(int id) {
        this.id = id;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Identifier){
            return id == ((Identifier)obj).id;
        } else {
            return false;
        }
    }
}
