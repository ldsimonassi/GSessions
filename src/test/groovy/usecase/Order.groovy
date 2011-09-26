package usecase

class Order {
	static persistent= ['address', 'items']
	String address
	List<Item> items= new ArrayList<Item>()
	
	String toString(){
		return "Order: Address= $address items:$items"
	}
}
