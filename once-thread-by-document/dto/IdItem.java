package dto;

public abstract class IdItem  {

    private long id;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "IdItem{" +
                "id=" + id +
                '}';
    }
}
