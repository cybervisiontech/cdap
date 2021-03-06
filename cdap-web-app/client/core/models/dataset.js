/*
 * Dataset Model
 */

define(['core/models/element'], function (Element) {

	var Model = Element.extend({
		type: 'Dataset',
		plural: 'Datasets',
		href: function () {
			return '#/datasets/' + this.get('id');
		}.property('id'),

		init: function() {

			this._super();

			if (!this.get('id')) {
				this.set('id', this.get('name'));
			}

			this.trackMetric('/system/datasets/{id}/dataset.size.mb', 'currents', 'storage', false,
			                 {'buffer': 70, transform: function(x) { return x * C.Util.BYTES_IN_MBYTE; } });

		},

		interpolate: function (path) {
			return path.replace(/\{id\}/, this.get('id'));

		}

	});

	Model.reopenClass({
		type: 'Dataset',
		kind: 'Model',
		find: function (dataset_id, http) {
			var promise = Ember.Deferred.create();

			http.rest('datasets', dataset_id, function (model, error) {

				model = C.Dataset.create(model);
				promise.resolve(model);

			});

			return promise;
		}
	});

	return Model;

});